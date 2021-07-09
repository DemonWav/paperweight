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

package io.papermc.paperweight.userdev

import io.papermc.paperweight.DownloadService
import io.papermc.paperweight.userdev.tasks.extractDevBundle
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.*

class PaperweightUser : Plugin<Project> {
    override fun apply(target: Project) {
        val userdev = target.extensions.create(PAPERWEIGHT_EXTENSION, PaperweightUserExtension::class)

        target.gradle.sharedServices.registerIfAbsent("download", DownloadService::class) {}

        target.tasks.register<Delete>("cleanCache") {
            group = "paperweight"
            description = "Delete the project setup cache and task outputs."
            delete(target.layout.cache)
        }

        target.configurations.create(DEV_BUNDLE_CONFIG)
        target.configurations.create(DECOMPILER_CONFIG)
        target.configurations.create(PARAM_MAPPINGS_CONFIG)
        target.configurations.create(REMAPPER_CONFIG)
        target.configurations.create(MOJANG_MAPPED_SERVER_CONFIG)
        target.configurations.create(MINECRAFT_LIBRARIES_CONFIG) {
            exclude("junit", "junit") // json-simple exposes junit for some reason
        }
        target.configurations.create(PAPER_API_CONFIG)

        val userdevTasks = UserdevTasks(target)

        target.afterEvaluate {
            val jar = project.tasks.named<AbstractArchiveTask>("jar") {
                archiveClassifier.set("dev")
            }
            val devJarTask = if (project.tasks.findByName("shadowJar") != null) {
                project.tasks.named<AbstractArchiveTask>("shadowJar") {
                    archiveClassifier.set("dev-all")
                }
            } else {
                jar
            }
            userdevTasks.reobfJar {
                inputJar.set(devJarTask.flatMap { it.archiveFile })
            }

            configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
                extendsFrom(
                    configurations.named(MOJANG_MAPPED_SERVER_CONFIG).get(),
                    configurations.named(MINECRAFT_LIBRARIES_CONFIG).get(),
                    configurations.named(PAPER_API_CONFIG).get()
                )
            }

            extractDevBundle(
                target.layout.cache.resolve(paperTaskOutputDir("extractDevBundle")),
                target.configurations.named(DEV_BUNDLE_CONFIG).map { it.singleFile }.convertToPath()
            )

            target.repositories {
                maven(userdevTasks.devBundleConfig.map { it.buildData.paramMappings.url }.get()) {
                    content { onlyForConfigurations(PARAM_MAPPINGS_CONFIG) }
                }
                maven(userdevTasks.devBundleConfig.map { it.remap.dep.url }.get()) {
                    content { onlyForConfigurations(REMAPPER_CONFIG) }
                }
                maven(userdevTasks.devBundleConfig.map { it.decompile.dep.url }.get()) {
                    content { onlyForConfigurations(DECOMPILER_CONFIG) }
                }
                for (repo in userdevTasks.devBundleConfig.map { it.buildData.libraryRepositories }.get()) {
                    maven(repo) {
                        content {
                            onlyForConfigurations(
                                MINECRAFT_LIBRARIES_CONFIG,
                                PAPER_API_CONFIG,
                                JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
                            )
                        }
                    }
                }

                ivy(layout.cache.resolve(IVY_REPOSITORY)) {
                    content {
                        val parts = userdevTasks.devBundleConfig.map { it.mappedServerCoordinates.split(":") }.get()
                        includeModule(parts[0], parts[1])
                    }
                    patternLayout {
                        artifact(IvyArtifactRepository.MAVEN_ARTIFACT_PATTERN)
                        ivy(IvyArtifactRepository.MAVEN_IVY_PATTERN)
                        setM2compatible(true)
                    }
                    metadataSources(IvyArtifactRepository.MetadataSources::ivyDescriptor)
                    isAllowInsecureProtocol = true
                    resolve.isDynamicMode = false
                }
            }

            target.dependencies {
                for (dep in userdevTasks.devBundleConfig.map { it.decompile.dep.coordinates }.get()) {
                    DECOMPILER_CONFIG(dep)
                }
                for (dep in userdevTasks.devBundleConfig.map { it.buildData.paramMappings.coordinates }.get()) {
                    PARAM_MAPPINGS_CONFIG(dep)
                }
                for (dep in userdevTasks.devBundleConfig.map { it.remap.dep.coordinates }.get()) {
                    REMAPPER_CONFIG(dep)
                }

                for (lib in userdevTasks.devBundleConfig.map { it.buildData.libraryDependencies }.get()) {
                    MINECRAFT_LIBRARIES_CONFIG(lib)
                }

                PAPER_API_CONFIG(userdevTasks.devBundleConfig.map { it.buildData.apiCoordinates }.get())
                PAPER_API_CONFIG(userdevTasks.devBundleConfig.map { it.buildData.mojangApiCoordinates }.get())
                MOJANG_MAPPED_SERVER_CONFIG(userdevTasks.devBundleConfig.map { it.mappedServerCoordinates }.get())
            }
        }
    }
}
