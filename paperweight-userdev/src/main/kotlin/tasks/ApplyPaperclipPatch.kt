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

import io.papermc.paperweight.tasks.JavaLauncherTask
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.paperTaskOutput
import kotlin.io.path.*
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ApplyPaperclipPatch : JavaLauncherTask() {
    @get:InputFile
    abstract val paperclip: RegularFileProperty

    @get:OutputFile
    abstract val patchedJar: RegularFileProperty

    override fun init() {
        super.init()
        patchedJar.convention(defaultOutput())
    }

    @TaskAction
    fun run() {
        patchPaperclip()
    }

    private fun patchPaperclip() {
        val work = createTempDirectory()
        val logFile = layout.cache.resolve(paperTaskOutput("log"))
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
        patched.copyTo(patchedJar.path, overwrite = true)
        work.deleteRecursively()
    }
}
