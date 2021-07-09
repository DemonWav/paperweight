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

package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.constants.*
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*

fun TaskProvider<Jar>.configurePaperclipJar(
    target: Project,
    generatePaperclipPatch: TaskProvider<GeneratePaperclipPatch>
) = configure { configurePaperclipJar(target, generatePaperclipPatch) }

fun Jar.configurePaperclipJar(
    target: Project,
    generatePaperclipPatch: TaskProvider<GeneratePaperclipPatch>
) {
    with(target.tasks.named("jar", Jar::class).get())

    val paperclipConfig = target.configurations.named(PAPERCLIP_CONFIG)
    dependsOn(paperclipConfig, generatePaperclipPatch)

    val paperclipZip = target.zipTree(paperclipConfig.map { it.singleFile })
    from(paperclipZip) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(target.zipTree(generatePaperclipPatch.flatMap { it.outputZip }))

    manifest.from(paperclipZip.matching { include("META-INF/MANIFEST.MF") }.elements.map { it.single() })
}
