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

fun installToIvyRepo(
    repo: Path,
    artifactCoordinates: String,
    sourcesJar: Path,
    binaryJar: Path
) {
    val (group, name, version, versionDir) = parseCoordinates(artifactCoordinates, repo)

    versionDir.createDirectories()

    sourcesJar.copyTo(versionDir.resolve("$name-$version-sources.jar"), overwrite = true)
    binaryJar.copyTo(versionDir.resolve("$name-$version.jar"), overwrite = true)

    val ivy = versionDir.resolve("ivy-$version.xml")
    // todo
    // a little trolling
    val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ivy-module xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://ant.apache.org/ivy/schemas/ivy.xsd" version="2.0">
            <info organisation="$group" module="$name" revision="$version" status="release">
            </info>
            <dependencies>
            </dependencies>
        </ivy-module>

    """.trimIndent()
    ivy.writeText(xml, Charsets.UTF_8)
}

private fun parseCoordinates(coordinatesString: String, root: Path): ArtifactLocation {
    val parts = coordinatesString.split(":")
    val group = parts[0]
    val groupDir = root.resolve(group.replace(".", "/"))
    val name = parts[1]
    val nameDir = groupDir.resolve(name)
    val version = parts[2]
    val versionDir = nameDir.resolve(version)
    return ArtifactLocation(group, name, version, versionDir)
}

private data class ArtifactLocation(
    val group: String,
    val name: String,
    val version: String,
    val versionDir: Path
)
