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

package io.papermc.paperweight.util

import java.nio.file.Path
import kotlin.io.path.*

fun filterJar(
    inputJar: Path,
    outputJar: Path,
    includes: List<String>,
    predicate: (Path) -> Boolean = { false }
) {
    val target = outputJar.resolveSibling("${outputJar.name}.dir")
    target.createDirectories()

    inputJar.openZip().use { zip ->
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
