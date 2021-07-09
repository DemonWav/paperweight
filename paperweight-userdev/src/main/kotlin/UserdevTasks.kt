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

package io.papermc.paperweight.userdev

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.tasks.GenerateDevBundle.DevBundleConfig
import io.papermc.paperweight.userdev.tasks.ApplyDevBundlePatches
import io.papermc.paperweight.userdev.tasks.ApplyPaperclipPatch
import io.papermc.paperweight.userdev.tasks.ExtractDevBundle
import io.papermc.paperweight.userdev.tasks.FilterPaperJar
import io.papermc.paperweight.userdev.tasks.InstallToIvyRepo
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*

class UserdevTasks(
    project: Project,
    tasks: TaskContainer = project.tasks,
    cache: Path = project.layout.cache
) {
    val extractDevBundle by tasks.registering<ExtractDevBundle> {
        devBundleZip.fileProvider(project.configurations.named(DEV_BUNDLE_CONFIG).map { it.singleFile })
        outputFolder.set(cache.resolve(paperTaskOutputDir()))
    }
    val devBundleConfig = extractDevBundle.map {
        val configFile = it.outputFolder.file("config.json").path
        val config: DevBundleConfig = configFile.bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader)
        }
        config
    }

    val downloadServerJar by tasks.registering<DownloadServerJar> {
        downloadUrl.set(devBundleConfig.map { it.buildData.serverUrl })
        downloader.set(project.download)
    }

    val filterVanillaJar by tasks.registering<FilterJar> {
        inputJar.set(downloadServerJar.flatMap { it.outputJar })
        includes.set(devBundleConfig.map { it.buildData.vanillaJarIncludes })
    }

    val downloadMcManifest by tasks.registering<DownloadTask> {
        url.set(MC_MANIFEST_URL)
        outputFile.set(cache.resolve(MC_MANIFEST))

        outputs.upToDateWhen { false }

        downloader.set(project.download)
    }
    private val mcManifest = downloadMcManifest.flatMap { it.outputFile }.map { gson.fromJson<MinecraftManifest>(it) }

    val downloadMcVersionManifest by tasks.registering<DownloadTask> {
        url.set(
            mcManifest.zip(devBundleConfig.map { it.minecraftVersion }) { manifest, version ->
                manifest.versions.first { it.id == version }.url
            }
        )
        outputFile.set(cache.resolve(VERSION_JSON))

        downloader.set(project.download)
    }
    private val versionManifest = downloadMcVersionManifest.flatMap { it.outputFile }.map { gson.fromJson<JsonObject>(it) }

    val setupMcLibraries by tasks.registering<SetupMcLibraries> {
        dependencies.set(
            versionManifest.map { version ->
                version["libraries"].array.map { library ->
                    library["name"].string
                }
            }
        )
        outputFile.set(cache.resolve(MC_LIBRARIES))
    }

    val downloadMappings by tasks.registering<DownloadTask> {
        url.set(
            versionManifest.map { version ->
                version["downloads"]["server_mappings"]["url"].string
            }
        )
        outputFile.set(cache.resolve(SERVER_MAPPINGS))

        downloader.set(project.download)
    }

    val downloadMcLibraries by tasks.registering<DownloadMcLibraries> {
        mcLibrariesFile.set(setupMcLibraries.flatMap { it.outputFile })
        mcRepo.set(MC_LIBRARY_URL)
        outputDir.set(cache.resolve(MINECRAFT_JARS_PATH))
        sourcesOutputDir.set(cache.resolve(MINECRAFT_SOURCES_PATH))

        downloader.set(project.download)
    }

    val generateMappings by tasks.registering<GenerateMappings> {
        vanillaJar.set(filterVanillaJar.flatMap { it.outputJar })
        libraries.from(downloadMcLibraries.map { it.outputDir.asFileTree })

        vanillaMappings.set(downloadMappings.flatMap { it.outputFile })
        paramMappings.fileProvider(project.configurations.named(PARAM_MAPPINGS_CONFIG).map { it.singleFile })

        outputMappings.set(cache.resolve(MOJANG_YARN_MAPPINGS))
    }

    val remapMinecraftJar by tasks.registering<RemapJar> {
        inputJar.set(filterVanillaJar.flatMap { it.outputJar })
        mappingsFile.set(generateMappings.flatMap { it.outputMappings })
        fromNamespace.set(OBF_NAMESPACE)
        toNamespace.set(DEOBF_NAMESPACE)
        remapper.from(project.configurations.named(REMAPPER_CONFIG))
    }

    val decompileMinecraftJar by tasks.registering<RunForgeFlower> {
        executable.from(project.configurations.named(DECOMPILER_CONFIG))

        inputJar.set(remapMinecraftJar.flatMap { it.outputJar })
        libraries.from(downloadMcLibraries.map { it.outputDir.asFileTree })
    }

    val applyDevBundlePatches by tasks.registering<ApplyDevBundlePatches> {
        inputZip.set(decompileMinecraftJar.flatMap { it.outputJar })
        devBundlePatches.set(extractDevBundle.flatMap { task -> task.outputFolder.dir(devBundleConfig.map { it.patchDir }) })
    }

    val patchPaperclip by tasks.registering<ApplyPaperclipPatch> {
        dependsOn(extractDevBundle)
        paperclip.set(extractDevBundle.flatMap { task -> task.outputFolder.file(devBundleConfig.map { it.buildData.mojangMappedPaperclipFile }) })
    }

    val filterPaperJar by tasks.registering<FilterPaperJar> {
        inputJar.set(patchPaperclip.flatMap { it.patchedJar })
        sourcesJar.set(applyDevBundlePatches.flatMap { it.outputZip })
        includes.set(listOf("/org/bukkit/craftbukkit/**"))
    }

    val setupPaperweightWorkspace by tasks.registering<InstallToIvyRepo> {
        group = "paperweight"
        description = "Setup Mojang mapped Paper with sources attached for plugin development."
        artifactCoordinates.set(devBundleConfig.map { it.mappedServerCoordinates })
        binaryJar.set(filterPaperJar.flatMap { it.outputJar })
        sourcesJar.set(applyDevBundlePatches.flatMap { it.outputZip })
    }

    val reobfJar by tasks.registering<RemapJar> {
        group = "paperweight"
        description = "Remap the compiled plugin jar to Spigot's obfuscated runtime names."
        outputJar.convention(project.layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))
        mappingsFile.set(extractDevBundle.flatMap { task -> task.outputFolder.file(devBundleConfig.map { it.buildData.reobfMappingsFile }) })
        fromNamespace.set(DEOBF_NAMESPACE)
        toNamespace.set(SPIGOT_NAMESPACE)
        remapper.from(project.configurations.named(REMAPPER_CONFIG))
        remapClasspath.from(project.configurations.named(MOJANG_MAPPED_SERVER_CONFIG))
    }
}
