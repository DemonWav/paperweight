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

package io.papermc.paperweight.core.taskcontainers

import io.papermc.paperweight.core.ext
import io.papermc.paperweight.core.extension.PaperweightCoreExtension
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*

@Suppress("MemberVisibilityCanBePrivate")
open class AllTasks(
    project: Project,
    tasks: TaskContainer = project.tasks,
    cache: Path = project.layout.cache,
    extension: PaperweightCoreExtension = project.ext,
) : SpigotTasks(project) {

    val mergeAdditionalAts by tasks.registering<MergeAccessTransforms> {
        firstFile.set(mergeGeneratedAts.flatMap { it.outputFile })
        secondFile.set(extension.paper.additionalAts.fileExists(project))
    }

    val applyMergedAt by tasks.registering<ApplyAccessTransform> {
        inputJar.set(fixJar.flatMap { it.outputJar })
        atFile.set(mergeAdditionalAts.flatMap { it.outputFile })
    }

    val copyResources by tasks.registering<CopyResources> {
        inputJar.set(applyMergedAt.flatMap { it.outputJar })
        vanillaJar.set(downloadServerJar.flatMap { it.outputJar })
        includes.set(listOf("/data/**", "/assets/**", "version.json", "yggdrasil_session_pubkey.der", "pack.mcmeta"))

        outputJar.set(cache.resolve(FINAL_REMAPPED_JAR))
    }

    val decompileJar by tasks.registering<RunForgeFlower> {
        executable.from(project.configurations.named(DECOMPILER_CONFIG))

        inputJar.set(copyResources.flatMap { it.outputJar })
        libraries.from(downloadMcLibraries.map { it.outputDir.asFileTree })
    }

    val applyApiPatches by tasks.registering<ApplyGitPatches> {
        group = "paper"
        description = "Setup the Paper-API project"

        if (project.isBaseExecution) {
            outputs.upToDateWhen { false }
        }
        printOutput.set(project.isBaseExecution)

        branch.set("HEAD")
        upstreamBranch.set("upstream")
        upstream.set(patchSpigotApi.flatMap { it.outputDir })
        patchDir.set(extension.paper.spigotApiPatchDir)
        unneededFiles.value(listOf("README.md"))

        outputDir.set(extension.paper.paperApiDir)
    }

    @Suppress("DuplicatedCode")
    val applyServerPatches by tasks.registering<ApplyPaperPatches> {
        group = "paper"
        description = "Setup the Paper-Server project"

        if (project.isBaseExecution) {
            outputs.upToDateWhen { false }
        }
        printOutput.set(project.isBaseExecution)

        patchDir.set(extension.paper.spigotServerPatchDir)
        remappedSource.set(remapSpigotSources.flatMap { it.sourcesOutputZip })
        remappedTests.set(remapSpigotSources.flatMap { it.testsOutputZip })
        caseOnlyClassNameChanges.set(cleanupMappings.flatMap { it.caseOnlyNameChanges })
        upstreamDir.set(patchSpigotServer.flatMap { it.outputDir })
        sourceMcDevJar.set(decompileJar.flatMap { it.outputJar })
        mcLibrariesDir.set(downloadMcLibraries.flatMap { it.sourcesOutputDir })
        devImports.set(extension.paper.devImports.fileExists(project))
        unneededFiles.value(listOf("nms-patches", "applyPatches.sh", "CONTRIBUTING.md", "makePatches.sh", "README.md"))

        outputDir.set(extension.paper.paperServerDir)
    }

    val applyPatches by tasks.registering<Task> {
        group = "paper"
        description = "Set up the Paper development environment"
        dependsOn(applyApiPatches, applyServerPatches)
    }

    val rebuildApiPatches by tasks.registering<RebuildPaperPatches> {
        group = "paper"
        description = "Rebuilds patches to api"
        inputDir.set(extension.paper.paperApiDir)
        baseRef.set("base")

        patchDir.set(extension.paper.spigotApiPatchDir)
    }

    val rebuildServerPatches by tasks.registering<RebuildPaperPatches> {
        group = "paper"
        description = "Rebuilds patches to server"
        inputDir.set(extension.paper.paperServerDir)
        baseRef.set("base")

        patchDir.set(extension.paper.spigotServerPatchDir)
    }

    @Suppress("unused")
    val rebuildPatches by tasks.registering<Task> {
        group = "paper"
        description = "Rebuilds patches to api and server"
        dependsOn(rebuildApiPatches, rebuildServerPatches)
    }

    val generateReobfMappings by tasks.registering<GenerateReobfMappings> {
        inputMappings.set(patchMappings.flatMap { it.outputMappings })
        notchToSpigotMappings.set(generateSpigotMappings.flatMap { it.notchToSpigotMappings })
        sourceMappings.set(generateMappings.flatMap { it.outputMappings })

        reobfMappings.set(cache.resolve(REOBF_MOJANG_SPIGOT_MAPPINGS))
    }

    val patchReobfMappings by tasks.registering<PatchMappings> {
        inputMappings.set(generateReobfMappings.flatMap { it.reobfMappings })
        patch.set(extension.paper.reobfMappingsPatch.fileExists(project))

        fromNamespace.set(DEOBF_NAMESPACE)
        toNamespace.set(SPIGOT_NAMESPACE)

        outputMappings.set(cache.resolve(PATCHED_REOBF_MOJANG_SPIGOT_MAPPINGS))
    }

    @Suppress("unused")
    val makeMcDevSrc by tasks.registering<MakeMcDevSrc> {
        group = "paper"
        source.set(decompileJar.flatMap { it.outputJar })
        paperServerDir.set(extension.paper.paperServerDir)
        target.set(cache.resolve(MC_DEV_DIR))
    }
}
