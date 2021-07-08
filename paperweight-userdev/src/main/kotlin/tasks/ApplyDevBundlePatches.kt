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

import io.papermc.paperweight.tasks.ZippedTask
import io.papermc.paperweight.util.Git
import io.papermc.paperweight.util.path
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory

abstract class ApplyDevBundlePatches : ZippedTask() {
    @get:InputDirectory
    abstract val devBundlePatches: DirectoryProperty

    override fun run(rootDir: Path) {
        Git.checkForGit()
        applyDevBundlePatches(rootDir)
    }

    private fun applyDevBundlePatches(rootDir: Path) {
        val git = Git(rootDir)

        Files.walk(devBundlePatches.path).use { stream ->
            stream.forEach {
                if (it.name.endsWith(".patch")) {
                    git("apply", it.absolutePathString()).executeOut()
                } else if (it.isRegularFile()) {
                    val destination = rootDir.resolve(it.relativeTo(devBundlePatches.path))
                    destination.parent.createDirectories()
                    it.copyTo(destination, overwrite = true)
                }
            }
        }
    }
}
