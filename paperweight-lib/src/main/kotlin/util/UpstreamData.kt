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

package io.papermc.paperweight.util

import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.RegularFile

data class UpstreamData(
    val vanillaJar: Path,
    val initialRemapJar: Path,
    val remappedJar: Path,
    val decompiledJar: Path,
    val mcVersion: String,
    val libDir: Path,
    val libSourceDir: Path,
    val libFile: Path?,
    val mappings: Path,
    val notchToSpigotMappings: Path,
    val sourceMappings: Path,
    val reobfPackagesToFix: List<String>?,
    val vanillaIncludes: List<String>,
    val paramMappings: MavenDep
)

fun readUpstreamData(inputFile: RegularFile): UpstreamData? {
    return inputFile.convertToPathOrNull()?.let { file ->
        if (file.isRegularFile()) {
            gson.fromJson(file)
        } else {
            null
        }
    }
}
