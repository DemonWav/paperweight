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
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import io.papermc.paperweight.configuration.download
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.userdev.configuration.applyDevBundlePatches
import io.papermc.paperweight.userdev.configuration.extractDevBundle
import io.papermc.paperweight.userdev.configuration.filterPaperJar
import io.papermc.paperweight.userdev.configuration.installToIvyRepo
import io.papermc.paperweight.userdev.configuration.patchPaperclip
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.workers.WorkerExecutor

class UserdevConfiguration(
    project: Project,
    workerExecutor: WorkerExecutor,
    javaToolchainService: JavaToolchainService,
    cache: Path = project.layout.cache
) {
    val extractedBundle: Path = cache.resolve(paperConfigurationOutput("extractDevBundle", "dir"))
    private val extractDevBundle = extractDevBundle(
        extractedBundle,
        project.configurations.named(DEV_BUNDLE_CONFIG).map { it.singleFile }.convertToPath()
    )
    private val devBundleChanged = extractDevBundle.first
    val devBundleConfig = extractDevBundle.second

    val vanillaServerJar = download(
        "vanilla minecraft server jar",
        project.download,
        devBundleChanged,
        devBundleConfig.buildData.serverUrl,
        cache.resolve(paperConfigurationOutput("downloadServerJar", "jar"))
    )

    val filteredVanillaServerJar: Path = run {
        val filteredJar = cache.resolve(paperConfigurationOutput("filterJar", "jar"))
        if (devBundleChanged || !filteredJar.hasCorrect256()) {
            project.logger.lifecycle(":filtering vanilla server jar")
            filterJar(
                vanillaServerJar,
                filteredJar,
                devBundleConfig.buildData.vanillaJarIncludes
            )
            filteredJar.writeSha256()
        }
        filteredJar
    }

    private val minecraftManifestJson = download(
        "minecraft manifest",
        project.download,
        devBundleChanged,
        MC_MANIFEST_URL,
        cache.resolve(MC_MANIFEST)
    )
    val minecraftManifest = gson.fromJson<MinecraftManifest>(minecraftManifestJson)

    private val minecraftVersionManifestJson = download(
        "minecraft version manifest",
        project.download,
        devBundleChanged,
        minecraftManifest.versions.first { it.id == devBundleConfig.minecraftVersion }.url,
        cache.resolve(VERSION_JSON)
    )
    val minecraftVersionManifest = gson.fromJson<JsonObject>(minecraftVersionManifestJson)

    val mojangServerMappings = download(
        "mojang server mappings",
        project.download,
        devBundleChanged,
        minecraftVersionManifest["downloads"]["server_mappings"]["url"].string,
        cache.resolve(SERVER_MAPPINGS)
    )

    val minecraftLibrariesFile: Path = run {
        val librariesFile = cache.resolve(MC_LIBRARIES)
        setupMinecraftLibraries(
            minecraftVersionManifest["libraries"].array.map { lib ->
                lib["name"].string
            },
            librariesFile
        )
        librariesFile
    }

    private fun hashFiles(files: List<Path>): String =
        files.sortedBy { it.name }.joinToString("\n") {
            "${it.name}:${toHex(it.hashFile(digestSha256()))}"
        }

    private fun hashLibraries(jars: Path, sources: Path): String =
        hashFiles(
            sequenceOf(jars, sources)
                .filter { it.isDirectory() }
                .flatMap { it.listDirectoryEntries("*.jar") }
                .toList()
        )

    private val minecraftLibraries: Pair<Path, Path> = run {
        val jars = cache.resolve(MINECRAFT_JARS_PATH)
        val sources = cache.resolve(MINECRAFT_SOURCES_PATH)

        val hashesFile = cache.resolve(paperConfigurationOutput("libraries", "hashes"))
        val upToDate = !devBundleChanged &&
            hashesFile.isRegularFile() &&
            hashesFile.readText(Charsets.UTF_8) == hashLibraries(jars, sources)
        if (upToDate) {
            return@run jars to sources
        }

        project.logger.lifecycle(":downloading minecraft libraries")
        downloadMinecraftLibraries(
            download = project.download,
            workerExecutor = workerExecutor,
            out = jars,
            sourcesOut = null, // sources, // we don't use sources jars for anything in userdev currently
            mcRepo = MC_LIBRARY_URL,
            mcLibrariesFile = minecraftLibrariesFile
        )
        workerExecutor.await()
        hashesFile.parent.createDirectories()
        hashesFile.writeText(hashLibraries(jars, sources))
        jars to sources
    }
    val minecraftLibraryJars = minecraftLibraries.first

    val mojangPlusYarnMappings: Path by lazy {
        val mappingsFile = cache.resolve(MOJANG_YARN_MAPPINGS)

        if (!devBundleChanged && mappingsFile.hasCorrect256()) {
            return@lazy mappingsFile
        }

        project.logger.lifecycle(":generating mappings")
        generateMappings(
            vanillaJarPath = filteredVanillaServerJar,
            libraryPaths = minecraftLibraryJars.listDirectoryEntries("*.jar"),
            vanillaMappingsPath = mojangServerMappings,
            paramMappingsPath = project.configurations.named(PARAM_MAPPINGS_CONFIG).map { it.singleFile }.convertToPath(),
            outputMappingsPath = mappingsFile,
            workerExecutor = workerExecutor,
            launcher = javaToolchainService.defaultJavaLauncher(project).get()
        )
        workerExecutor.await()
        mappingsFile.writeSha256()
        mappingsFile
    }

    val mappedMinecraftServerJar: Path by lazy {
        val mappings = mojangPlusYarnMappings // init lazy value

        val name = "mappedMinecraftServerJar"
        val output = cache.resolve(paperConfigurationOutput(name, "jar"))
        val logFile = cache.resolve(paperConfigurationOutput(name, "log"))

        if (!devBundleChanged && output.hasCorrect256()) {
            return@lazy output
        }

        project.logger.lifecycle(":remapping minecraft server jar")
        runTinyRemapper(
            argsList = devBundleConfig.remap.args,
            logFile = logFile,
            inputJar = filteredVanillaServerJar,
            mappingsFile = mappings,
            fromNamespace = OBF_NAMESPACE,
            toNamespace = DEOBF_NAMESPACE,
            remapClasspath = minecraftLibraryJars.listDirectoryEntries("*.jar"),
            remapper = project.configurations.named(REMAPPER_CONFIG).get(),
            outputJar = output,
            launcher = javaToolchainService.defaultJavaLauncher(project).get(),
            workingDir = cache
        )
        output.writeSha256()
        output
    }

    val decompiledMinecraftServerJar: Path by lazy {
        val minecraftJar = mappedMinecraftServerJar // init lazy value

        val name = "decompileMinecraftServerJar"
        val output = cache.resolve(paperConfigurationOutput(name, "jar"))
        val logFile = cache.resolve(paperConfigurationOutput(name, "log"))

        if (!devBundleChanged && output.hasCorrect256()) {
            return@lazy output
        }

        project.logger.lifecycle(":decompiling mapped minecraft server jar")
        runForgeFlower(
            argsList = devBundleConfig.decompile.args,
            logFile = logFile,
            workingDir = cache,
            executable = project.configurations.named(DECOMPILER_CONFIG).get(),
            inputJar = minecraftJar,
            libraries = minecraftLibraryJars.listDirectoryEntries("*.jar"),
            outputJar = output,
            javaLauncher = javaToolchainService.defaultJavaLauncher(project).get()
        )
        output.writeSha256()
        output
    }

    val patchedSourcesJar: Path by lazy {
        val decompileJar = decompiledMinecraftServerJar // init lazy value

        val output = cache.resolve(paperConfigurationOutput("patchedSourcesJar", "jar"))

        if (!devBundleChanged && output.hasCorrect256()) {
            return@lazy output
        }

        project.logger.lifecycle(":applying patches to decompiled jar")
        applyDevBundlePatches(
            decompileJar,
            extractedBundle.resolve(devBundleConfig.patchDir),
            output
        )
        output.writeSha256()
        output
    }

    val mojangMappedPaperJar: Path by lazy {
        val name = "applyMojangMappedPaperclipPatch"
        val output = cache.resolve(paperConfigurationOutput(name, "jar"))
        val logFile = cache.resolve(paperConfigurationOutput(name, "log"))

        if (!devBundleChanged && output.hasCorrect256()) {
            return@lazy output
        }

        project.logger.lifecycle(":applying mojang mapped paperclip patch")
        patchPaperclip(
            project = project,
            launcher = javaToolchainService.defaultJavaLauncher(project).get(),
            paperclip = extractedBundle.resolve(devBundleConfig.buildData.mojangMappedPaperclipFile),
            outputJar = output,
            logFile = logFile
        )
        output.writeSha256()
        output
    }

    val filteredMojangMappedPaperJar: Path by lazy {
        val input = mojangMappedPaperJar // init lazy value
        val output = cache.resolve(paperConfigurationOutput("filteredPaperServerJar", "jar"))

        if (!devBundleChanged && output.hasCorrect256()) {
            return@lazy output
        }
        filterPaperJar(patchedSourcesJar, input, output, devBundleConfig.buildData.relocations)
        output.writeSha256()
        output
    }

    fun installServerArtifactToIvyRepository(cache: Path, repo: Path) {
        // init lazy values
        val sources = patchedSourcesJar
        val serverJar = filteredMojangMappedPaperJar

        val hashes = cache.resolve(paperConfigurationOutput("installedArtifacts", "hashes"))

        val upToDate = hashes.isRegularFile() &&
            hashes.readText(Charsets.UTF_8) == hashFiles(listOf(patchedSourcesJar, filteredMojangMappedPaperJar))
        if (upToDate) {
            return
        }

        println(":installing server artifacts to cache")
        installToIvyRepo(
            repo,
            devBundleConfig.mappedServerCoordinates,
            sources,
            serverJar
        )

        hashes.writeText(hashFiles(listOf(patchedSourcesJar, filteredMojangMappedPaperJar)), Charsets.UTF_8)

        println(":done setting up paperweight userdev workspace")
    }
}
