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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.provider.Provider

data class MavenDep(val url: String, val coordinates: List<String>)

fun determineMavenDep(url: Provider<String>, configuration: Provider<Configuration>): MavenDep {
    return MavenDep(url.get(), determineArtifactCoordinates(configuration.get()))
}

fun determineArtifactCoordinates(configuration: Configuration): List<String> {
    return configuration.dependencies.filterIsInstance<ModuleDependency>().map { dep ->
        sequenceOf(
            "group" to dep.group,
            "name" to dep.name,
            "version" to dep.version,
            "classifier" to (dep.artifacts.singleOrNull()?.classifier ?: "")
        ).filter {
            if (it.second == null) error("No ${it.first}: $dep")
            it.second?.isNotEmpty() ?: false
        }.map {
            it.second
        }.joinToString(":")
    }
}
