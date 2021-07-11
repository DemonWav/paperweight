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

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*

abstract class PaperweightUserExtension(
    project: Project,
    configuration: Provider<UserdevConfiguration>,
    objects: ObjectFactory
) {
    /**
     * Whether to inject the Paper maven repository for use by the dev bundle configuration.
     *
     * True by default to allow easily resolving Paper development bundles.
     */
    val injectPaperRepository: Property<Boolean> = objects.property<Boolean>().convention(true)

    /**
     * Provides a runnable Mojang mapped server jar, extracted from the current dev bundle.
     */
    val mojangMappedPaperServerJar: Provider<RegularFile> = objects.fileProperty().value(
        project.layout.file(
            configuration.map {
                it.extractedBundle.resolve(it.devBundleConfig.buildData.mojangMappedPaperclipFile).toFile()
            }
        )
    )

    /**
     * Provides the Minecraft version of the current dev bundle.
     */
    val minecraftVersion: Provider<String> = objects.property<String>().value(
        configuration.map { it.devBundleConfig.minecraftVersion }
    )
}
