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

package io.papermc.paperweight.tasks

import io.papermc.paperweight.extension.Relocation
import io.papermc.paperweight.extension.RelocationWrapper
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.apache.tools.ant.types.selectors.SelectorUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class GenerateDevBundle : DefaultTask() {

    @get:InputFile
    abstract val decompiledJar: RegularFileProperty

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:InputFile
    abstract val mojangMappedPaperclipFile: RegularFileProperty

    @get:Input
    abstract val mappedServerCoordinates: Property<String>

    @get:Input
    abstract val vanillaJarIncludes: ListProperty<String>

    @get:Input
    abstract val vanillaServerLibraries: ListProperty<String>

    @get:Input
    abstract val libraryRepositories: ListProperty<String>

    @get:Internal
    abstract val serverProject: Property<Project>

    @get:Input
    abstract val apiCoordinates: Property<String>

    @get:Input
    abstract val mojangApiCoordinates: Property<String>

    @get:Input
    abstract val relocations: Property<String>

    @get:Input
    abstract val paramMappingsUrl: Property<String>

    @get:Input
    abstract val paramMappingsCoordinates: Property<String>

    @get:Input
    abstract val decompilerUrl: Property<String>

    @get:Classpath
    abstract val decompilerConfig: Property<Configuration>

    @get:Input
    abstract val remapperUrl: Property<String>

    @get:Classpath
    abstract val remapperConfig: Property<Configuration>

    @get:InputFile
    abstract val reobfMappingsFile: RegularFileProperty

    @get:OutputFile
    abstract val devBundleFile: RegularFileProperty

    @TaskAction
    fun run() {
        val devBundle = devBundleFile.path
        devBundle.deleteForcefully()
        devBundle.parent.createDirectories()

        val tempPatchDir = createTempDirectory("devBundlePatches")
        try {
            generatePatches(tempPatchDir)

            val dataDir = "data"
            val patchesDir = "patches"
            val config = createBundleConfig(dataDir, patchesDir)

            devBundle.writeZip().use { zip ->
                zip.getPath("config.json").bufferedWriter(Charsets.UTF_8).use { writer ->
                    gson.toJson(config, writer)
                }

                val dataZip = zip.getPath(dataDir)
                dataZip.createDirectories()
                reobfMappingsFile.path.copyTo(dataZip.resolve(reobfMappingsFileName))
                mojangMappedPaperclipFile.path.copyTo(dataZip.resolve(mojangMappedPaperclipFileName))

                val patchesZip = zip.getPath(patchesDir)
                tempPatchDir.copyRecursivelyTo(patchesZip)
            }
        } finally {
            tempPatchDir.deleteRecursively()
        }
    }

    private fun generatePatches(output: Path) {
        val workingDir = project.layout.cache.resolve(paperTaskOutput("tmpdir"))
        workingDir.createDirectories()
        sourceDir.path.copyRecursivelyTo(workingDir)

        relocate(relocations(), workingDir)

        Files.walk(workingDir).use { stream ->
            decompiledJar.path.openZip().use { decompJar ->
                val decompRoot = decompJar.rootDirectories.single()

                for (file in stream) {
                    if (file.isDirectory()) {
                        continue
                    }
                    val relativeFile = file.relativeTo(workingDir)
                    val relativeFilePath = relativeFile.invariantSeparatorsPathString
                    val decompFile = decompRoot.resolve(relativeFilePath)

                    if (decompFile.notExists()) {
                        val outputFile = output.resolve(relativeFilePath)
                        outputFile.parent.createDirectories()
                        file.copyTo(outputFile)
                    } else {
                        val diffText = diffFiles(relativeFilePath, decompFile, file)
                        val patchName = relativeFile.name + ".patch"
                        val outputFile = output.resolve(relativeFilePath).resolveSibling(patchName)
                        if (diffText.isNotBlank()) {
                            outputFile.parent.createDirectories()
                            outputFile.writeText(diffText)
                        }
                    }
                }
            }
        }

        workingDir.deleteRecursively()
    }

    private fun relocate(relocations: List<Relocation>, workingDir: Path) {
        val wrappedRelocations = relocations.map { RelocationWrapper(it) }

        Files.walk(workingDir).use { stream ->
            stream.filter { it.isRegularFile() && it.name.endsWith(".java") }
                .forEach { path -> replaceRelocationsInFile(path, wrappedRelocations) }
        }

        relocateFiles(wrappedRelocations, workingDir)
    }

    private fun replaceRelocationsInFile(path: Path, relocations: List<RelocationWrapper>) {
        var content = path.readText(Charsets.UTF_8)

        // Use hashes as intermediary to avoid double relocating

        for (relocation in relocations) {
            content = content.replace(relocation.fromDot, '.' + relocation.hashCode().toString())
                .replace(relocation.fromSlash, '/' + relocation.hashCode().toString())
        }

        for (relocation in relocations) {
            content = content.replace('.' + relocation.hashCode().toString(), relocation.toDot)
                .replace('/' + relocation.hashCode().toString(), relocation.toSlash)
        }

        path.writeText(content, Charsets.UTF_8)
    }

    private fun relocateFiles(relocations: List<RelocationWrapper>, workingDir: Path) {
        Files.walk(workingDir).use { stream ->
            stream.filter { it.isRegularFile() && it.name.endsWith(".java") }
                .forEach { path ->
                    val potential = path.relativeTo(workingDir).pathString
                    for (relocation in relocations) {
                        if (potential.startsWith(relocation.fromSlash)) {
                            if (excluded(relocation.relocation, potential)) {
                                break
                            }
                            val dest = workingDir.resolve(potential.replace(relocation.fromSlash, relocation.toSlash))
                            dest.parent.createDirectories()
                            path.moveTo(dest)
                            break
                        }
                    }
                }
        }
    }

    private fun excluded(relocation: Relocation, potential: String): Boolean =
        relocation.excludes.map {
            it.replace('.', '/')
        }.any { exclude ->
            SelectorUtils.matchPath(exclude, potential, true)
        }

    private fun diffFiles(fileName: String, original: Path, patched: Path): String {
        val dir = createTempDirectory("diff")
        try {
            val oldFile = dir.resolve("old.java")
            val newFile = dir.resolve("new.java")
            original.copyTo(oldFile)
            patched.copyTo(newFile)

            val args = listOf(
                "diff",
                "--color=never",
                "-ud",
                "--label", "a/$fileName",
                oldFile.absolutePathString(),
                "--label", "b/$fileName",
                newFile.absolutePathString(),
            )

            return runDiff(dir, args)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runDiff(dir: Path, args: List<String>): String {
        val process = ProcessBuilder(args)
            .directory(dir)
            .redirectErrorStream(true)
            .start()

        val out = ByteArrayOutputStream()
        process.inputStream.use { input ->
            input.copyTo(out)
        }

        return String(out.toByteArray(), Charset.defaultCharset())
            .replace(System.getProperty("line.separator"), "\n")
    }

    @Suppress("SameParameterValue")
    private fun createBundleConfig(dataTargetDir: String, patchTargetDir: String): DevBundleConfig {
        return DevBundleConfig(
            minecraftVersion = minecraftVersion.get(),
            mappedServerCoordinates = mappedServerCoordinates.get(),
            apiCoordinates = "${apiCoordinates.get()}:${serverProject.get().version}",
            mojangApiCoordinates = "${mojangApiCoordinates.get()}:${serverProject.get().version}",
            buildData = createBuildDataConfig(dataTargetDir),
            decompile = createDecompileRunner(),
            remap = createRemapRunner(),
            patchDir = patchTargetDir
        )
    }

    private fun relocations(): List<Relocation> = gson.fromJson(relocations.get())

    private fun createBuildDataConfig(targetDir: String): BuildData {
        return BuildData(
            paramMappings = MavenDep(paramMappingsUrl.get(), listOf(paramMappingsCoordinates.get())),
            reobfMappingsFile = "$targetDir/$reobfMappingsFileName",
            mojangMappedPaperclipFile = "$targetDir/$mojangMappedPaperclipFileName",
            vanillaJarIncludes = vanillaJarIncludes.get(),
            libraryDependencies = determineLibraries(vanillaServerLibraries.get()),
            libraryRepositories = libraryRepositories.get(),
            relocations = relocations()
        )
    }

    private fun determineLibraries(vanillaServerLibraries: List<String>): Set<String> {
        val new = arrayListOf<String>()

        for (dependency in serverProject.get().configurations.named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).get().dependencies) {
            // don't want project dependencies
            if (dependency is ExternalModuleDependency) {
                val version = sequenceOf(
                    dependency.versionConstraint.strictVersion,
                    dependency.versionConstraint.requiredVersion,
                    dependency.versionConstraint.preferredVersion,
                    dependency.version
                ).filterNotNull().filter { it.isNotBlank() }.first()
                new += sequenceOf(
                    dependency.group,
                    dependency.name,
                    version
                ).joinToString(":")
            }
        }

        val result = vanillaServerLibraries.toMutableSet()
        result += new

        // Remove relocated libraries
        val libs = relocations().mapNotNull { it.owningLibraryCoordinates }
        result.removeIf { coords -> libs.any { coords.startsWith(it) } }

        return result
    }

    private fun createDecompileRunner(): Runner {
        return Runner(
            dep = determineMavenDep(decompilerUrl, decompilerConfig),
            args = forgeFlowerArgList
        )
    }

    private fun createRemapRunner(): Runner {
        return Runner(
            dep = determineMavenDep(remapperUrl, remapperConfig),
            args = tinyRemapperArgsList
        )
    }

    data class DevBundleConfig(
        val minecraftVersion: String,
        val mappedServerCoordinates: String,
        val apiCoordinates: String,
        val mojangApiCoordinates: String,
        val buildData: BuildData,
        val decompile: Runner,
        val remap: Runner,
        val patchDir: String
    )

    data class BuildData(
        val paramMappings: MavenDep,
        val reobfMappingsFile: String,
        val mojangMappedPaperclipFile: String,
        val vanillaJarIncludes: List<String>,
        val libraryDependencies: Set<String>,
        val libraryRepositories: List<String>,
        val relocations: List<Relocation>
    )

    data class Runner(val dep: MavenDep, val args: List<String>)

    companion object {
        const val reobfMappingsFileName = "$DEOBF_NAMESPACE-$SPIGOT_NAMESPACE-reobf.tiny"
        const val mojangMappedPaperclipFileName = "paperclip-$DEOBF_NAMESPACE.jar"
    }
}
