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

package io.papermc.paperweight.core.extension

import io.papermc.paperweight.util.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*

open class PaperweightCoreExtension(objects: ObjectFactory, layout: ProjectLayout) {

    @Suppress("MemberVisibilityCanBePrivate")
    val workDir: DirectoryProperty = objects.dirWithDefault(layout, "work")

    val minecraftVersion: Property<String> = objects.property()
    val serverProject: Property<Project> = objects.property()

    val paramMappingsRepo: Property<String> = objects.property()
    val decompileRepo: Property<String> = objects.property()
    val remapRepo: Property<String> = objects.property()

    val vanillaJarIncludes: ListProperty<String> = objects.listProperty<String>().convention(listOf("/*.class", "/net/minecraft/**", "/com/mojang/math/**"))

    @Suppress("MemberVisibilityCanBePrivate")
    val craftBukkit = CraftBukkitExtension(objects, workDir)
    val spigot = SpigotExtension(objects, workDir)
    val paper = PaperExtension(objects, layout)

    @Suppress("unused")
    fun craftBukkit(action: Action<in CraftBukkitExtension>) {
        action.execute(craftBukkit)
    }

    @Suppress("unused")
    fun spigot(action: Action<in SpigotExtension>) {
        action.execute(spigot)
    }

    @Suppress("unused")
    fun paper(action: Action<in PaperExtension>) {
        action.execute(paper)
    }
}
