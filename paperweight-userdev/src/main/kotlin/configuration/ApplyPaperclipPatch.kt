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

package io.papermc.paperweight.userdev.configuration

import io.papermc.paperweight.util.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLauncher

fun patchPaperclip(
    project: Project,
    launcher: JavaLauncher,
    paperclip: Path,
    outputJar: Path,
    logFile: Path
) {
    val work = createTempDirectory()
    logFile.deleteForcefully()
    launcher.runJar(
        classpath = project.files(paperclip),
        workingDir = work,
        logFile = logFile,
        jvmArgs = listOf("-Dpaperclip.patchonly=true"),
        args = arrayOf()
    )
    val patched = work.resolve("cache").listDirectoryEntries()
        .find { it.name.startsWith("patched") } ?: error("Can't find patched jar!")
    patched.copyTo(outputJar, overwrite = true)
    work.deleteRecursively()
}
