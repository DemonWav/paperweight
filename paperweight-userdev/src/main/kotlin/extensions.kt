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

import io.papermc.paperweight.util.constants.*
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.kotlin.dsl.*

/**
 * Adds a dependency to the dev bundle [org.gradle.api.artifacts.Configuration].
 * Defaults to adding Paper's dev bundle, but by overriding the default arguments,
 * any dependency can be added.
 *
 * @param version dependency version
 * @param group dependency group
 * @param artifactId dependency artifactId
 * @param configuration Dependency configuration
 * @param classifier Dependency classifier
 * @param ext Dependency extension
 * @param devBundleConfigurationName Name of the development bundle [org.gradle.api.artifacts.Configuration]
 */
fun DependencyHandlerScope.paperweightDevBundle(
    version: String? = null,
    group: String = "io.papermc.paper",
    artifactId: String = "dev-bundle",
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null,
    devBundleConfigurationName: String = DEV_BUNDLE_CONFIG
): ExternalModuleDependency = devBundleConfigurationName(group, artifactId, version, configuration, classifier, ext)
