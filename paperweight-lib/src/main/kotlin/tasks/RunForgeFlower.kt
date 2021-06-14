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

import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import kotlin.io.path.*
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

fun forgeFlowerArgList(): List<String> {
    return listOf(
        "-ind=    ",
        "-din=1",
        "-rbr=1",
        "-dgs=1",
        "-asc=1",
        "-rsy=1",
        "-iec=1",
        "-jvn=0",
        "-isl=0",
        "-iib=1",
        "-log=TRACE",
        "-cfg",
        "{libraries}",
        "{input}",
        "{output}"
    )
}

fun createForgeFlowerArgs(libraries: String, input: String, output: String): List<String> {
    return forgeFlowerArgList().map {
        when (it) {
            "{libraries}" -> libraries
            "{input}" -> input
            "{output}" -> output
            else -> it
        }
    }
}

@CacheableTask
abstract class RunForgeFlower : JavaLauncherTask() {

    @get:Classpath
    abstract val executable: ConfigurableFileCollection

    @get:Classpath
    abstract val inputJar: RegularFileProperty

    @get:CompileClasspath
    abstract val libraries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Internal
    abstract val jvmargs: ListProperty<String>

    override fun init() {
        super.init()

        jvmargs.convention(listOf("-Xmx4G"))
        outputJar.convention(defaultOutput())
    }

    @TaskAction
    fun run() {
        val out = outputJar.path
        val target = out.resolveSibling("${out.name}.dir")
        if (target.exists()) {
            target.deleteRecursively()
        }
        target.createDirectories()

        val libs = libraries.files.mapTo(ArrayList()) { it.toPath() }
        libs.sort()
        val tempFile = createTempFile("paperweight", "txt")

        try {
            tempFile.bufferedWriter().use { writer ->
                for (lib in libs) {
                    if (lib.isLibraryJar) {
                        writer.appendLine("-e=${lib.absolutePathString()}")
                    }
                }
            }

            val argList = createForgeFlowerArgs(
                tempFile.absolutePathString(),
                inputJar.path.absolutePathString(),
                target.absolutePathString()
            )

            val logFile = layout.cache.resolve(paperTaskOutput("log"))
            logFile.deleteForcefully()

            launcher.runJar(executable, layout.cache, logFile, jvmArgs = jvmargs.get(), args = argList.toTypedArray())

            // FernFlower is weird with how it does directory output
            target.resolve(inputJar.path.name).moveTo(out, overwrite = true)
            target.deleteRecursively()
        } finally {
            tempFile.deleteForcefully()
        }
    }
}
