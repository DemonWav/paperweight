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
import io.papermc.paperweight.PaperweightException
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.util.internal.NameMatcher
import org.gradle.workers.WorkerExecutor

abstract class PaperweightUser : Plugin<Project> {
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:Inject
    abstract val javaToolchainService: JavaToolchainService

    override fun apply(target: Project) {
        target.gradle.sharedServices.registerIfAbsent("download", DownloadService::class) {}

        val cleanCache by target.tasks.registering<Delete> {
            group = "paperweight"
            description = "Delete the project setup cache and task outputs."
            delete(target.layout.cache)
        }

        target.configurations.create(DEV_BUNDLE_CONFIG)
        target.configurations.create(DECOMPILER_CONFIG)
        target.configurations.create(PARAM_MAPPINGS_CONFIG)
        target.configurations.create(REMAPPER_CONFIG) {
            isTransitive = false // we use a fat jar for tiny-remapper, so we don't need it's transitive deps (which aren't on the quilt repo)
        }
        target.configurations.create(MOJANG_MAPPED_SERVER_CONFIG)
        target.configurations.create(MINECRAFT_LIBRARIES_CONFIG) {
            exclude("junit", "junit") // json-simple exposes junit for some reason
        }
        target.configurations.create(PAPER_API_CONFIG)

        val userdevConfiguration by lazy { UserdevConfiguration(target, workerExecutor, javaToolchainService) }
        val devBundleConfig by lazy { userdevConfiguration.devBundleConfig }

        val userdev = target.extensions.create(
            PAPERWEIGHT_EXTENSION,
            PaperweightUserExtension::class,
            target,
            target.provider { userdevConfiguration },
            target.objects
        )

        val reobfJar by target.tasks.registering<RemapJar> {
            group = "paperweight"
            description = "Remap the compiled plugin jar to Spigot's obfuscated runtime names."

            outputJar.convention(project.layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))

            mappingsFile.pathProvider(target.provider { userdevConfiguration.extractedBundle.resolve(devBundleConfig.buildData.reobfMappingsFile) })
            remapClasspath.from(target.provider { userdevConfiguration.mojangMappedPaperJar })

            fromNamespace.set(DEOBF_NAMESPACE)
            toNamespace.set(SPIGOT_NAMESPACE)

            remapper.from(project.configurations.named(REMAPPER_CONFIG))
        }

        // Manually check if cleanCache is a target, and skip setup.
        // Gradle moved NameMatcher to internal packages in 7.1, so this solution isn't ideal,
        // but it does work and allows using the cleanCache task without setting up the workspace first
        val cleaningCache = target.gradle.startParameter.taskRequests
            .map {
                it.args.any { arg ->
                    NameMatcher().find(arg, target.tasks.names) == cleanCache.name
                }
            }
            .any { it }
        if (cleaningCache) return

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
            reobfJar {
                inputJar.set(devJarTask.flatMap { it.archiveFile })
            }

            configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
                extendsFrom(
                    configurations.named(MOJANG_MAPPED_SERVER_CONFIG).get(),
                    configurations.named(MINECRAFT_LIBRARIES_CONFIG).get(),
                    configurations.named(PAPER_API_CONFIG).get()
                )
            }

            if (configurations.named(DEV_BUNDLE_CONFIG).get().isEmpty) {
                throw PaperweightException(
                    "paperweight requires a development bundle to be added to the 'paperweightDevelopmentBundle' configuration in" +
                        " order to function. Use the paperweightDevBundle extension function to do this easily."
                )
            }

            target.repositories {
                if (userdev.injectPaperRepository.get()) {
                    maven(PAPER_MAVEN_REPO_URL) {
                        content { onlyForConfigurations(DEV_BUNDLE_CONFIG) }
                    }
                }

                maven(devBundleConfig.buildData.paramMappings.url) {
                    content { onlyForConfigurations(PARAM_MAPPINGS_CONFIG) }
                }
                maven(devBundleConfig.remap.dep.url) {
                    content { onlyForConfigurations(REMAPPER_CONFIG) }
                }
                maven(devBundleConfig.decompile.dep.url) {
                    content { onlyForConfigurations(DECOMPILER_CONFIG) }
                }
                for (repo in devBundleConfig.buildData.libraryRepositories) {
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
                        val parts = devBundleConfig.mappedServerCoordinates.split(":")
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
                for (dep in devBundleConfig.decompile.dep.coordinates) {
                    DECOMPILER_CONFIG(dep)
                }
                for (dep in devBundleConfig.buildData.paramMappings.coordinates) {
                    PARAM_MAPPINGS_CONFIG(dep)
                }
                for (dep in devBundleConfig.remap.dep.coordinates) {
                    REMAPPER_CONFIG(dep)
                }

                for (lib in devBundleConfig.buildData.libraryDependencies) {
                    MINECRAFT_LIBRARIES_CONFIG(lib)
                }

                PAPER_API_CONFIG(devBundleConfig.apiCoordinates)
                PAPER_API_CONFIG(devBundleConfig.mojangApiCoordinates)
                MOJANG_MAPPED_SERVER_CONFIG(devBundleConfig.mappedServerCoordinates)
            }

            userdevConfiguration.installServerArtifactToIvyRepository(
                target.layout.cache,
                target.layout.cache.resolve(IVY_REPOSITORY)
            )
        }
    }
}
