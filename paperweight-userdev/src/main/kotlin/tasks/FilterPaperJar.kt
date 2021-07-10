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

import io.papermc.paperweight.extension.Relocation
import io.papermc.paperweight.extension.RelocationWrapper
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile

abstract class FilterPaperJar : FilterJar() {
    @get:InputFile
    abstract val sourcesJar: RegularFileProperty

    @get:Input
    abstract val relocations: Property<String>

    override fun run() {
        // Include relocated packages
        val patternIncludes = includes.get().toMutableList()
        val rel = gson.fromJson<List<Relocation>>(relocations.get()).map { RelocationWrapper(it) }
        for (relocation in rel) {
            patternIncludes += '/' + relocation.toSlash + "/**"
            for (exclude in relocation.relocation.excludes) {
                patternIncludes += '/' + exclude.replace('.', '/')
            }
        }

        val includedFiles = collectIncludes()
        filterJar(patternIncludes) { path ->
            val str = path.pathString
            if (str.contains('$')) {
                includedFiles.contains(str.split("$")[0] + ".class")
            } else {
                includedFiles.contains(str)
            }
        }
    }

    private fun collectIncludes(): Set<String> {
        val extraIncludes = hashSetOf<String>()

        // Include all files we have sources for
        iterateJar(sourcesJar.path) { entry ->
            if (entry.isRegularFile()) {
                val string = entry.pathString
                extraIncludes += if (string.endsWith(".java")) {
                    string.substringBeforeLast(".java") + ".class"
                } else {
                    string
                }
            }
        }

        // Include non-class resource files from server jar
        iterateJar(inputJar.path) { entry ->
            if (entry.isRegularFile() && !entry.name.endsWith(".class")) {
                extraIncludes += entry.pathString
            }
        }

        return extraIncludes
    }

    private fun iterateJar(
        jar: Path,
        visitor: (Path) -> Unit
    ) = jar.openZip().use { fs ->
        fs.walk().use { stream ->
            stream.forEach { path ->
                visitor(path)
            }
        }
    }
}
