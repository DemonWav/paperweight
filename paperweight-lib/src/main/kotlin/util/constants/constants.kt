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

package io.papermc.paperweight.util.constants

import org.gradle.api.Task

const val PAPERWEIGHT_EXTENSION = "paperweight"
const val PAPERWEIGHT_DEBUG = "paperweight.debug"

const val MC_LIBRARY_URL = "https://libraries.minecraft.net/"

const val MC_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

const val PARAM_MAPPINGS_CONFIG = "paramMappings"
const val REMAPPER_CONFIG = "remapper"
const val DECOMPILER_CONFIG = "decompiler"
const val PAPERCLIP_CONFIG = "paperclip"
const val DEV_BUNDLE_CONFIG = "paperweightDevelopmentBundle"
const val MOJANG_MAPPED_SERVER_CONFIG = "mojangMappedServer"
const val MINECRAFT_LIBRARIES_CONFIG = "minecraftLibraries"
const val PAPER_API_CONFIG = "paperApi"

const val PARAM_MAPPINGS_REPO_NAME = "paperweightParamMappingsRepository"
const val DECOMPILER_REPO_NAME = "paperweightDecompilerRepository"
const val REMAPPER_REPO_NAME = "paperweightRemapperRepository"

const val CACHE_PATH = "caches"
private const val PAPER_PATH = "paperweight"

const val UPSTREAMS = "$PAPER_PATH/upstreams"
const val UPSTREAM_WORK_DIR_PROPERTY = "paperweightUpstreamWorkDir"
const val PAPERWEIGHT_PREPARE_DOWNSTREAM = "prepareForDownstream"
const val PAPERWEIGHT_DOWNSTREAM_FILE_PROPERTY = "paperweightDownstreamDataFile"

private const val JARS_PATH = "$PAPER_PATH/jars"
const val MINECRAFT_JARS_PATH = "$JARS_PATH/minecraft"
const val MINECRAFT_SOURCES_PATH = "$JARS_PATH/minecraft-sources"

const val SPIGOT_JARS_PATH = "$JARS_PATH/spigot"

private const val MAPPINGS_DIR = "$PAPER_PATH/mappings"
const val SERVER_MAPPINGS = "$MAPPINGS_DIR/server_mappings.txt"
const val MOJANG_YARN_MAPPINGS = "$MAPPINGS_DIR/official-mojang+yarn.tiny"

const val SPIGOT_MOJANG_YARN_MAPPINGS = "$MAPPINGS_DIR/spigot-mojang+yarn.tiny"
const val OBF_SPIGOT_MAPPINGS = "$MAPPINGS_DIR/official-spigot.tiny"
const val SPIGOT_MOJANG_YARN_FIELDS_MAPPINGS = "$MAPPINGS_DIR/spigot-mojang+yarn+fields.csrg"
const val CLEANED_SPIGOT_MOJANG_YARN_MAPPINGS = "$MAPPINGS_DIR/spigot-mojang+yarn-cleaned.tiny"
const val PATCHED_SPIGOT_MOJANG_YARN_MAPPINGS = "$MAPPINGS_DIR/spigot-mojang+yarn-patched.tiny"
const val REOBF_MOJANG_SPIGOT_MAPPINGS = "$MAPPINGS_DIR/mojang+yarn-spigot-reobf.tiny"
const val PATCHED_REOBF_MOJANG_SPIGOT_MAPPINGS = "$MAPPINGS_DIR/mojang+yarn-spigot-reobf-patched.tiny"

const val OBF_NAMESPACE = "official"
const val SPIGOT_NAMESPACE = "spigot"
const val DEOBF_NAMESPACE = "mojang+yarn"

private const val DATA_PATH = "$PAPER_PATH/data"
const val MC_MANIFEST = "$DATA_PATH/McManifest.json"
const val VERSION_JSON = "$DATA_PATH/McVersion.json"
const val MC_LIBRARIES = "$DATA_PATH/McLibraries.txt"
const val SERVER_LIBRARIES = "$DATA_PATH/ServerLibraries.txt"

private const val TASK_CACHE = "$PAPER_PATH/taskCache"

const val FINAL_REMAPPED_JAR = "$TASK_CACHE/minecraft.jar"

const val MC_DEV_DIR = "mcdev-source"

const val IVY_REPOSITORY = "$PAPER_PATH/ivyRepository"

fun Task.paperTaskOutput(ext: String) = paperTaskOutput(name, ext)
fun paperTaskOutput(name: String, ext: String) = "$TASK_CACHE/$name.$ext"
fun Task.paperTaskOutputDir() = paperTaskOutputDir(name)
fun paperTaskOutputDir(name: String) = "$TASK_CACHE/$name"
