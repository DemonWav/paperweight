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

import io.papermc.paperweight.PaperweightException
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher

fun tinyRemapperArgsList(): List<String> {
    return listOf(
        "{input}",
        "{output}",
        "{mappings}",
        "{from}",
        "{to}",
        "{classpath}",
        "--fixpackageaccess",
        "--renameinvalidlocals",
        "--threads=1",
        "--rebuildsourcefilenames"
    )
}

fun createTinyRemapperArgs(
    input: String,
    output: String,
    mappings: String,
    from: String,
    to: String,
    classpath: Array<String>
): List<String> {
    val result = mutableListOf<String>()
    for (arg in tinyRemapperArgsList()) {
        val mapped = when (arg) {
            "{input}" -> input
            "{output}" -> output
            "{mappings}" -> mappings
            "{from}" -> from
            "{to}" -> to
            "{classpath}" -> classpath
            else -> arg
        }
        when (mapped) {
            is String -> result += mapped
            is Array<*> -> mapped.mapTo(result) { it as? String ?: throw PaperweightException("Expected String! Got: '$it'.") }
            else -> throw PaperweightException("Don't know what to do with '$mapped'!")
        }
    }
    return result
}

fun runTinyRemapper(
    logFile: Path,
    inputJar: Path,
    mappingsFile: Path,
    fromNamespace: String,
    toNamespace: String,
    remapClasspath: List<Path>,
    remapper: FileCollection,
    outputJar: Path,
    launcher: JavaLauncher,
    workingDir: Path,
    jvmArgs: List<String> = listOf("-Xmx1G")
) {
    ensureDeleted(logFile)

    val args = createTinyRemapperArgs(
        inputJar.absolutePathString(),
        outputJar.absolutePathString(),
        mappingsFile.absolutePathString(),
        fromNamespace,
        toNamespace,
        remapClasspath.map { it.absolutePathString() }.toTypedArray()
    )

    ensureParentExists(logFile)
    launcher.runJar(remapper, workingDir, logFile, jvmArgs = jvmArgs, args = args.toTypedArray())
}

@CacheableTask
abstract class RemapJar : JavaLauncherTask() {

    @get:Classpath
    abstract val inputJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingsFile: RegularFileProperty

    @get:Input
    abstract val fromNamespace: Property<String>

    @get:Input
    abstract val toNamespace: Property<String>

    @get:CompileClasspath
    abstract val remapClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val remapper: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Internal
    abstract val jvmargs: ListProperty<String>

    override fun init() {
        super.init()

        outputJar.convention(defaultOutput())
        jvmargs.convention(listOf("-Xmx1G"))
    }

    @TaskAction
    fun run() {
        val logFile = layout.cache.resolve(paperTaskOutput("log"))
        runTinyRemapper(
            logFile,
            inputJar.path,
            mappingsFile.path,
            fromNamespace.get(),
            toNamespace.get(),
            remapClasspath.files.map { it.toPath() },
            remapper,
            outputJar.path,
            launcher.get(),
            layout.cache,
            jvmargs.get()
        )
    }
}
