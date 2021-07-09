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

package io.papermc.paperweight.patcher

import io.papermc.paperweight.patcher.upstream.DefaultPaperRepoPatcherUpstream
import io.papermc.paperweight.patcher.upstream.DefaultPatcherUpstream
import io.papermc.paperweight.patcher.upstream.DefaultRepoPatcherUpstream
import io.papermc.paperweight.patcher.upstream.PaperRepoPatcherUpstream
import io.papermc.paperweight.patcher.upstream.PatcherUpstream
import io.papermc.paperweight.patcher.upstream.RepoPatcherUpstream
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class PaperweightPatcherExtension(private val objects: ObjectFactory, layout: ProjectLayout, tasks: TaskContainer) {

    val serverProject: Property<Project> = objects.property()

    val buildDataDir: DirectoryProperty = objects.dirWithDefault(layout, "build-data")
    val devImports: RegularFileProperty = objects.fileFrom(buildDataDir, "dev-imports.txt")
    val reobfPackagesToFix: ListProperty<String> = objects.listProperty()

    val paramMappingsRepo: Property<String> = objects.property()
    val decompileRepo: Property<String> = objects.property()
    val remapRepo: Property<String> = objects.property()

    val upstreams: ExtensiblePolymorphicDomainObjectContainer<PatcherUpstream> = objects.polymorphicDomainObjectContainer(PatcherUpstream::class)

    /**
     * The directory upstreams should be checked out in. Paperweight will use the directory specified in the
     * following order, whichever is set first:
     *
     *  1. The value of the Gradle property `paperweightUpstreamWorkDir`.
     *  2. The value of this [upstreamsDir] property.
     *  3. The default location of <project_root>/.gradle/caches/paperweight/upstreams
     *
     * This means a project which is several upstreams deep will all use the upstreams directory defined by the root project.
     */
    val upstreamsDir: Property<Directory> = objects.directoryProperty().convention(layout.cacheDir(UPSTREAMS))

    init {
        upstreams.registerFactory(PatcherUpstream::class.java) { name -> DefaultPatcherUpstream(name, objects, tasks) }
        upstreams.registerFactory(RepoPatcherUpstream::class.java) { name -> DefaultRepoPatcherUpstream(name, objects, tasks, layout) }
        upstreams.registerFactory(PaperRepoPatcherUpstream::class.java) { name -> DefaultPaperRepoPatcherUpstream(name, objects, tasks, layout) }
    }

    fun usePaperUpstream(refProvider: Provider<String>, action: Action<PaperRepoPatcherUpstream>) {
        upstreams {
            register<PaperRepoPatcherUpstream>("paper") {
                url.set(github("PaperMC", "Paper"))
                ref.set(refProvider)

                action.execute(this)
            }
        }
    }

    fun useStandardUpstream(name: String, action: Action<RepoPatcherUpstream>) {
        upstreams {
            register<RepoPatcherUpstream>(name) {
                action.execute(this)
            }
        }
    }
}
