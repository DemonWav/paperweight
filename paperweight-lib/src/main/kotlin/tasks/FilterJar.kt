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
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class FilterJar : BaseTask() {

    @get:Classpath
    abstract val inputJar: RegularFileProperty

    @get:Input
    abstract val includes: ListProperty<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    override fun init() {
        outputJar.convention(defaultOutput())
    }

    @TaskAction
    open fun run() {
        filterJar(includes.get())
    }

    protected fun filterJar(
        includes: List<String>,
        predicate: (Path) -> Boolean = { false }
    ) {
        val out = outputJar.path
        val target = out.resolveSibling("${out.name}.dir")
        target.createDirectories()

        inputJar.path.openZip().use { zip ->
            val matchers = includes.map { zip.getPathMatcher("glob:$it") }

            zip.walk().use { stream ->
                stream.filter { p -> predicate(p) || matchers.any { matcher -> matcher.matches(p) } }
                    .forEach { p ->
                        val targetFile = target.resolve(p.absolutePathString().substring(1))
                        targetFile.parent.createDirectories()
                        p.copyTo(targetFile)
                    }
            }
        }

        zip(target, outputJar)
        target.deleteRecursively()
    }
}
