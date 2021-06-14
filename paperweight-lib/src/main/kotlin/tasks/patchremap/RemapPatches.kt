/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2021 Kyle Wood (DenWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 only, no later versions.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.paperweight.tasks.patchremap

import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*
import org.cadixdev.at.io.AccessTransformFormats
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.*

abstract class RemapPatches : BaseTask() {

    @get:InputDirectory
    abstract val inputPatchDir: DirectoryProperty

    @get:InputDirectory
    abstract val apiPatchDir: DirectoryProperty

    @get:InputFile
    abstract val mappingsFile: RegularFileProperty

    @get:InputFile
    abstract val ats: RegularFileProperty

    @get:Classpath
    abstract val classpathJars: ConfigurableFileCollection

    @get:InputDirectory
    abstract val spigotApiDir: DirectoryProperty

    @get:InputDirectory
    abstract val spigotServerDir: DirectoryProperty

    @get:InputFile
    abstract val spigotDecompJar: RegularFileProperty

    @get:InputDirectory
    abstract val mcLibrarySourcesDir: DirectoryProperty

    @get:InputFile
    abstract val devImports: RegularFileProperty

    @get:Input
    abstract val ignoreGitIgnore: Property<Boolean>

    @get:OutputDirectory
    abstract val outputPatchDir: DirectoryProperty

    @get:Internal
    @get:Option(
        option = "continue-remap",
        description = "For resuming, don't recreate remap dir and pick up where last run left off"
    )
    abstract val continueRemapping: Property<Boolean>

    @get:Internal
    @get:Option(
        option = "limit-patches",
        description = "For testing, you can limit the # of patches (e.g. --limit-patches=10)"
    )
    abstract val limitPatches: Property<String>

    @get:Inject
    abstract val providers: ProviderFactory

    override fun init() {
        continueRemapping.convention(false)
        ignoreGitIgnore.convention(Git.ignoreProperty(providers)).finalizeValueOnRead()
    }

    @TaskAction
    fun run() {
        val metaFile = layout.cache / "paperweight" / "remap-meta"
        val meta = if (metaFile.exists()) {
            if (continueRemapping.get()) {
                gson.fromJson<RemapMeta>(metaFile)
            } else {
                metaFile.deleteForcefully()
                null
            }
        } else {
            null
        }

        // Check patches
        val inputElements = inputPatchDir.path.listDirectoryEntries().sorted()
        if (inputElements.any { it.isRegularFile() }) {
            println("Remap patch input directory must only contain directories or patch files, not both")
            return
        }
        if (inputElements.size == 1) {
            println("No patches to remap, only 1 patch set found")
            return
        }

        val patchesToSkip = inputElements.dropLast(1).flatMap { it.listDirectoryEntries("*.patch").sorted() }
        val patchesToRemap = inputElements.last().listDirectoryEntries("*.patch").sorted()

        if (patchesToRemap.isEmpty()) {
            println("No input patches to remap found")
            return
        }

        val limit = limitPatches.map { it.toInt() }.orElse(patchesToRemap.size).get()

        val mappings = MappingFormats.TINY.read(mappingsFile.path, SPIGOT_NAMESPACE, DEOBF_NAMESPACE)

        // This should pull in any libraries needed for type bindings
        val configFiles = project.project(":Paper-Server").configurations["runtimeClasspath"].resolve().map { it.toPath() }
        val classpathFiles = classpathJars.map { it.toPath() } + configFiles

        // Remap output directory, after each output this directory will be re-named to the input directory below for
        // the next remap operation
        println("setting up repo")
        val tempApiDir = createWorkDir("patch-remap-api", source = spigotApiDir.path, recreate = !continueRemapping.get())
        val tempInputDir = createWorkDirByCloning(
            "patch-remap-input",
            source = spigotServerDir.path,
            recreate = !continueRemapping.get()
        )
        val tempOutputDir = createWorkDir("patch-remap-output")

        val sourceInputDir = tempInputDir.resolve("src/main/java")

        PatchSourceRemapWorker(
            mappings,
            AccessTransformFormats.FML.read(ats.path),
            listOf(*classpathFiles.toTypedArray(), tempApiDir.resolve("src/main/java")),
            sourceInputDir,
            tempOutputDir
        ).let { remapper ->
            val patchApplier = PatchApplier("remapped", "old", ignoreGitIgnore.get(), tempInputDir)

            if (!continueRemapping.get()) {
                // first run
                patchApplier.createBranches()

                // We need to include any missing classes for the patches later on
                McDev.importMcDev(
                    patches = patchesToSkip + patchesToRemap,
                    decompJar = spigotDecompJar.path,
                    importsFile = devImports.path,
                    librariesDir = mcLibrarySourcesDir.path,
                    targetDir = tempInputDir.resolve("src/main/java")
                )

                patchApplier.commitPlain("McDev imports")
            }

            if (meta == null || meta.stage == RemapStage.PRE_REMAP) {
                var foundResume = false
                val patchesToApply = patchesToSkip.dropWhile { patch ->
                    when {
                        meta == null -> false
                        meta.patchSet == patch.parent.name && meta.patchName == patch.name -> {
                            foundResume = true
                            true
                        }
                        else -> !foundResume
                    }
                }
                println("Applying ${patchesToApply.size} patches before remapping")
                for (patch in patchesToApply) {
                    metaFile.deleteForcefully()
                    metaFile.bufferedWriter().use { writer ->
                        gson.toJson(RemapMeta(RemapStage.PRE_REMAP, patch.parent.name, patch.name), writer)
                    }
                    patchApplier.applyPatch(patch)
                }

                patchApplier.checkoutRemapped() // Switch to remapped branch without checking out files

                remapper.remap() // Remap to new mappings
                patchApplier.commitInitialRemappedSource() // Initial commit of pre-remap sources mapped to new mappings
                patchApplier.checkoutOld() // Normal checkout back to pre-remap mappings branch
            } else if (patchApplier.isUnfinishedPatch()) {
                println("===========================")
                println("Finishing current patch")
                println("===========================")
                patchApplier.recordCommit()
                patchApplier.checkoutRemapped()
                remapper.remap()
                patchApplier.commitChanges()
                patchApplier.checkoutOld()
                println("===========================")
                println("done with current patch")
                println("===========================")
            }

            // Repo setup is done, we can begin the patch loop now
            var counter = 0
            var remapSkip = meta != null && meta.stage == RemapStage.REMAP
            for (patch in patchesToRemap) {
                if (remapSkip && meta != null) {
                    if (meta.patchSet == patch.parent.name && meta.patchName == patch.name) {
                        remapSkip = false
                        continue
                    }
                    continue
                }

                metaFile.deleteForcefully()
                metaFile.bufferedWriter().use { writer ->
                    gson.toJson(RemapMeta(RemapStage.REMAP, patch.parent.name, patch.name), writer)
                }

                println("===========================")
                println("attempting to remap $patch")
                println("===========================")
                patchApplier.applyPatch(patch) // Apply patch on Spigot mappings
                patchApplier.recordCommit() // Keep track of commit author, message, and time
                patchApplier.checkoutRemapped() // Switch to remapped branch without checkout out files
                remapper.remap() // Remap to new mappings
                patchApplier.commitChanges() // Commit the changes
                patchApplier.checkoutOld() // Normal checkout back to Spigot mappings branch
                println("===========================")
                println("done remapping patch $patch")
                println("===========================")

                counter++
                if (counter >= limit) {
                    break
                }
            }
            patchApplier.generatePatches(outputPatchDir.path)
        }
    }

    private fun createWorkDir(name: String, source: Path? = null, recreate: Boolean = true): Path {
        return layout.cache.resolve("paperweight").resolve(name).apply {
            if (recreate) {
                deleteRecursively()
                createDirectories()
                source?.copyRecursivelyTo(this)
            }
        }
    }

    private fun createWorkDirByCloning(name: String, source: Path, recreate: Boolean = true): Path {
        val workDir = layout.cache.resolve("paperweight")
        return workDir.resolve(name).apply {
            if (recreate) {
                deleteRecursively()
                createDirectories()
                Git(workDir)("clone", source.absolutePathString(), this.absolutePathString()).executeSilently()
            }
        }
    }

    data class RemapMeta(
        val stage: RemapStage,
        val patchSet: String,
        val patchName: String
    )

    enum class RemapStage {
        PRE_REMAP,
        REMAP
    }
}
