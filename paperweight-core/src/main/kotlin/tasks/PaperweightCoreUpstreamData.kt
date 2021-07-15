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

package io.papermc.paperweight.core.tasks

import io.papermc.paperweight.util.*
import javax.inject.Inject
import kotlin.io.path.*
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PaperweightCoreUpstreamData : DefaultTask() {

    @get:InputFile
    abstract val vanillaJar: RegularFileProperty

    @get:InputFile
    abstract val remappedJar: RegularFileProperty

    @get:InputFile
    abstract val initialRemapJar: RegularFileProperty

    @get:InputFile
    abstract val decompiledJar: RegularFileProperty

    @get:Input
    abstract val mcVersion: Property<String>

    @get:InputDirectory
    abstract val mcLibrariesDir: DirectoryProperty

    @get:InputDirectory
    abstract val mcLibrariesSourcesDir: DirectoryProperty

    @get:Input
    abstract val vanillaJarIncludes: ListProperty<String>

    @get:Optional
    @get:InputFile
    abstract val mcLibrariesFile: RegularFileProperty

    @get:InputFile
    abstract val mappings: RegularFileProperty

    @get:InputFile
    abstract val notchToSpigotMappings: RegularFileProperty

    @get:InputFile
    abstract val sourceMappings: RegularFileProperty

    @get:Input
    abstract val reobfPackagesToFix: ListProperty<String>

    @get:OutputFile
    abstract val dataFile: RegularFileProperty

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Input
    abstract val paramMappingsUrl: Property<String>

    @get:Classpath
    abstract val paramMappingsConfig: Property<Configuration>

    @TaskAction
    fun run() {
        val dataFilePath = dataFile.path

        dataFilePath.parent.createDirectories()

        val data = UpstreamData(
            vanillaJar.path,
            initialRemapJar.path,
            remappedJar.path,
            decompiledJar.path,
            mcVersion.get(),
            mcLibrariesDir.path,
            mcLibrariesSourcesDir.path,
            mcLibrariesFile.pathOrNull,
            mappings.path,
            notchToSpigotMappings.path,
            sourceMappings.path,
            reobfPackagesToFix.get(),
            vanillaJarIncludes.get(),
            determineMavenDep(paramMappingsUrl, paramMappingsConfig)
        )
        dataFilePath.bufferedWriter(Charsets.UTF_8).use { writer ->
            gson.toJson(data, writer)
        }
    }
}
