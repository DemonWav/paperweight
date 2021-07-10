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

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.*
import io.papermc.paperweight.DownloadService
import io.papermc.paperweight.PaperweightException
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.constants.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Optional
import java.util.concurrent.ThreadLocalRandom
import kotlin.experimental.and
import kotlin.io.path.*
import org.cadixdev.lorenz.merge.MergeResult
import org.cadixdev.lorenz.model.ClassMapping
import org.cadixdev.lorenz.model.MemberMapping
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*

val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().registerTypeHierarchyAdapter(Path::class.java, PathJsonConverter()).create()

class PathJsonConverter : JsonDeserializer<Path?>, JsonSerializer<Path?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Path? {
        return if (json != null) Paths.get(json.asString) else null
    }

    override fun serialize(src: Path?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

inline fun <reified T> Gson.fromJson(any: Any): T = when (any) {
    is String -> fromJson(any)
    else -> any.convertToPath().bufferedReader(Charsets.UTF_8).use { fromJson(it) }
}

val ProjectLayout.cache: Path
    get() = projectDirectory.file(".gradle/$CACHE_PATH").path

fun ProjectLayout.cacheDir(path: String) = projectDirectory.dir(".gradle/$CACHE_PATH").dir(path)
fun ProjectLayout.initSubmodules() {
    Git.checkForGit()
    Git(projectDirectory.path)("submodule", "update", "--init").executeOut()
}

fun <T : FileSystemLocation> Provider<out T>.fileExists(project: Project): Provider<out T?> {
    return flatMap { project.provider { it.takeIf { f -> f.path.exists() } } }
}

inline fun <reified T : Task> TaskContainer.providerFor(name: String): TaskProvider<T> {
    return if (names.contains(name)) {
        named<T>(name)
    } else {
        register<T>(name)
    }
}

inline fun <reified T : Task> TaskContainer.configureTask(name: String, noinline configure: T.() -> Unit): TaskProvider<T> {
    return if (names.contains(name)) {
        named(name, configure)
    } else {
        register(name, configure)
    }
}

@Suppress("UNCHECKED_CAST")
val Project.download: Provider<DownloadService>
    get() = gradle.sharedServices.registrations.getByName("download").service as Provider<DownloadService>

fun commentRegex(): Regex {
    return Regex("\\s*#.*")
}

val Project.isBaseExecution: Boolean
    get() = providers.gradleProperty(PAPERWEIGHT_PREPARE_DOWNSTREAM)
        .forUseAtConfigurationTime()
        .orElse(provider { "false" })
        .map { it == "false" }
        .get()

fun redirect(input: InputStream, out: OutputStream): Thread {
    return Thread {
        try {
            input.copyTo(out)
        } catch (e: Exception) {
            throw PaperweightException("", e)
        }
    }.apply {
        isDaemon = true
        start()
    }
}

object UselessOutputStream : OutputStream() {
    override fun write(b: Int) {
    }
}

class DelegatingOutputStream(vararg delegates: OutputStream) : OutputStream() {

    private val delegates: MutableSet<OutputStream> = Collections.newSetFromMap(IdentityHashMap())

    init {
        this.delegates.addAll(delegates)
    }

    override fun write(b: Int) {
        for (delegate in delegates) {
            delegate.write(b)
        }
    }
}

fun Any.convertToPath(): Path {
    return when (this) {
        is Path -> this
        is File -> this.toPath()
        is FileSystemLocation -> this.path
        is Provider<*> -> this.get().convertToPath()
        else -> throw PaperweightException("Unknown type representing a file: ${this.javaClass.name}")
    }
}

fun Any?.convertToPathOrNull(): Path? {
    if (this == null) {
        return null
    }
    return this.convertToPath()
}

fun Any.convertToUrl(): URL {
    return when (this) {
        is URL -> this
        is URI -> this.toURL()
        is String -> URI.create(this).toURL()
        is Provider<*> -> this.get().convertToUrl()
        else -> throw PaperweightException("Unknown URL type: ${this.javaClass.name}")
    }
}

fun ensureParentExists(vararg files: Any) {
    for (file in files) {
        val parent = file.convertToPath().parent
        try {
            parent.createDirectories()
        } catch (e: Exception) {
            throw PaperweightException("Failed to create directory $parent", e)
        }
    }
}

fun ensureDeleted(vararg files: Any) {
    for (file in files) {
        val f = file.convertToPath()
        try {
            f.deleteRecursively()
        } catch (e: Exception) {
            throw PaperweightException("Failed to delete file $f", e)
        }
    }
}

fun BaseTask.defaultOutput(name: String, ext: String): RegularFileProperty {
    return objects.fileProperty().convention {
        layout.cache.resolve(paperTaskOutput(name, ext)).toFile()
    }
}

fun BaseTask.defaultOutput(ext: String): RegularFileProperty {
    return defaultOutput(name, ext)
}

fun BaseTask.defaultOutput(): RegularFileProperty {
    return defaultOutput("jar")
}

val <T> Optional<T>.orNull: T?
    get() = orElse(null)

inline fun <reified T : Any> Project.contents(contentFile: RegularFileProperty, crossinline convert: (String) -> T): Provider<T> {
    return providers.fileContents(contentFile)
        .asText
        .forUseAtConfigurationTime()
        .map { convert(it) }
}

fun findOutputDir(baseFile: Path): Path {
    var dir: Path
    do {
        dir = baseFile.resolveSibling("${baseFile.name}-" + ThreadLocalRandom.current().nextInt())
    } while (dir.exists())
    return dir
}

val MemberMapping<*, *>.parentClass: ClassMapping<*, *>
    get() = parent as ClassMapping<*, *>

private val emptyMergeResult = MergeResult(null)
fun <T> emptyMergeResult(): MergeResult<T?> {
    @Suppress("UNCHECKED_CAST")
    return emptyMergeResult as MergeResult<T?>
}

inline fun <reified T : Task> TaskContainer.registering(noinline configuration: T.() -> Unit) = registering(T::class, configuration)
inline fun <reified T : Task> TaskContainer.registering() = registering(T::class)

fun digestSha256(): MessageDigest = try {
    MessageDigest.getInstance("SHA-256")
} catch (e: NoSuchAlgorithmException) {
    throw PaperweightException("Could not create SHA-256 hasher", e)
}

fun toHex(hash: ByteArray): String {
    val sb: StringBuilder = StringBuilder(hash.size * 2)
    for (aHash in hash) {
        sb.append("%02X".format(aHash and 0xFF.toByte()))
    }
    return sb.toString()
}
