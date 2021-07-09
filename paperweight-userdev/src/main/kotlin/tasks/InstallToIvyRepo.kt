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

package io.papermc.paperweight.userdev.tasks

import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class InstallToIvyRepo : BaseTask() {
    @get:Input
    abstract val artifactCoordinates: Property<String>

    @get:InputFile
    abstract val sourcesJar: RegularFileProperty

    @get:InputFile
    abstract val binaryJar: RegularFileProperty

    @TaskAction
    private fun run() {
        val root = project.layout.cache.resolve(IVY_REPOSITORY)

        val (group, name, version, versionDir) = parseCoordinates(artifactCoordinates.get(), root)

        versionDir.createDirectories()

        sourcesJar.path.copyTo(versionDir.resolve("$name-$version-sources.jar"), overwrite = true)
        binaryJar.path.copyTo(versionDir.resolve("$name-$version.jar"), overwrite = true)

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
}
