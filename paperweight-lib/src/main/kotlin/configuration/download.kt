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

package io.papermc.paperweight.configuration

import io.papermc.paperweight.DownloadService
import io.papermc.paperweight.util.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.provider.Provider

/**
 * Downloads a file if the hash no longer matches what was recorded on last download, or if the [forceUpdate] flag is set
 */
fun download(
    downloadName: String,
    download: Provider<DownloadService>,
    forceUpdate: Boolean,
    serverUrl: Any,
    destination: Path
): Path {
    val upToDate = !forceUpdate && destination.hasCorrect256()
    if (upToDate) {
        return destination
    }
    println(":downloading $downloadName")

    destination.parent.createDirectories()
    download.get().download(
        serverUrl,
        destination
    )
    destination.writeSha256()

    return destination
}
