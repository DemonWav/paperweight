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

package io.papermc.paperweight.extension

import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*

abstract class RelocationExtension(objects: ObjectFactory) {
    val relocations = objects.listProperty<Relocation>()

    fun relocate(
        owningLibraryCoordinates: String,
        relocation: Pair<String, String>,
        config: Relocation.() -> Unit = {}
    ) {
        relocations.add(Relocation(owningLibraryCoordinates, relocation.first, relocation.second, arrayListOf()).apply(config))
    }

    fun relocate(
        relocation: Pair<String, String>,
        config: Relocation.() -> Unit = {}
    ) {
        relocations.add(Relocation(null, relocation.first, relocation.second, arrayListOf()).apply(config))
    }
}

data class Relocation(
    val owningLibraryCoordinates: String?,
    val fromPackage: String,
    val toPackage: String,
    val excludes: List<String>
) {
    fun exclude(exclude: String) {
        (excludes as MutableList) += exclude
    }
}

data class RelocationWrapper(
    val relocation: Relocation,
    val fromSlash: String = relocation.fromPackage.replace('.', '/'),
    val fromDot: String = relocation.fromPackage.replace('/', '.'),
    val toSlash: String = relocation.toPackage.replace('.', '/'),
    val toDot: String = relocation.toPackage.replace('/', '.')
)
