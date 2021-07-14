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

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import io.papermc.paperweight.DownloadService
import io.papermc.paperweight.core.ext
import io.papermc.paperweight.core.extension.PaperweightCoreExtension
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*

@Suppress("MemberVisibilityCanBePrivate")
open class InitialTasks(
    project: Project,
    tasks: TaskContainer = project.tasks,
    cache: Path = project.layout.cache,
    extension: PaperweightCoreExtension = project.ext,
    downloadService: Provider<DownloadService> = project.download
) {

    val downloadMcManifest by tasks.registering<DownloadTask> {
        url.set(MC_MANIFEST_URL)
        outputFile.set(cache.resolve(MC_MANIFEST))

        outputs.upToDateWhen { false }

        downloader.set(downloadService)
    }
    private val mcManifest = downloadMcManifest.flatMap { it.outputFile }.map { gson.fromJson<MinecraftManifest>(it) }

    val downloadMcVersionManifest by tasks.registering<DownloadTask> {
        url.set(
            mcManifest.zip(extension.minecraftVersion) { manifest, version ->
                manifest.versions.first { it.id == version }.url
            }
        )
        outputFile.set(cache.resolve(VERSION_JSON))

        downloader.set(downloadService)
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

        downloader.set(downloadService)
    }

    val downloadServerJar by tasks.registering<DownloadServerJar> {
        downloadUrl.set(
            versionManifest.map { version ->
                version["downloads"]["server"]["url"].string
            }
        )

        downloader.set(downloadService)
    }
}
