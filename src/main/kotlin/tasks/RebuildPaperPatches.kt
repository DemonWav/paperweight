package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.Git
import io.papermc.paperweight.util.file
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class RebuildPaperPatches : ControllableOutputTask() {

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty
    @get:Console
    abstract val server: Property<Boolean>
    @get:Option(option = "filter-patches", description = "Controlls if patches should be cleaned up, defaults to true")
    abstract val filterPatches: Property<Boolean>

    @get:OutputDirectory
    abstract val patchDir: DirectoryProperty

    override fun init() {
        printOutput.convention(true)
        filterPatches.convention(true)
        server.convention(false)
    }

    @TaskAction
    fun run() {
        val what =inputDir.file.name
        val patchFolder = patchDir.file
        if (!patchFolder.exists()) {
            patchFolder.mkdirs()
        }

        if (printOutput.get()) {
            println("Formatting patches for $what...")
        }

        if (inputDir.file.resolve(".git/rebase-apply").exists()) {
            // in middle of a rebase, be smarter
            if (printOutput.get()) {
                println("REBASE DETECTED - PARTIAL SAVE")
                val last= inputDir.file.resolve("/.git/rebase-apply/last").readText().toInt()
                val next = inputDir.file.resolve("/.git/rebase-apply/next").readText().toInt()
                val orderedFiles = patchFolder.listFiles { f -> f.name.endsWith(".patch") }!!
                orderedFiles.sort()

                for (i in 1..last) {
                    if (i < next) {
                        orderedFiles[i].delete();
                    }
                }
            }
        } else {
            patchFolder.deleteRecursively()
            patchFolder.mkdirs()
        }

        Git(inputDir.file)("format-patch", "--zero-commit", "--full-index", "--no-signature", "--no-stat", "-N", "-o", patchFolder.absolutePath, if (server.get()) "base" else "upstream/upstream").executeSilently()
        Git(patchFolder)("add", "-A", ".").executeSilently()

        if (filterPatches.get()) {
            cleanupPatches()
        }

        if (printOutput.get()) {
            println("  Patches saved for $what to ${patchFolder.name}/")
        }
    }

    private fun cleanupPatches() {
        patchDir.file.listFiles { f -> f.name.endsWith(".patch") }?.forEach { patch ->
            if (printOutput.get()) {
                println(patch.name)
            }

            // TODO implement patch cleanup
            //
            //        diffs=$($gitcmd diff --staged "$patch" | grep --color=none -E "^(\+|\-)" | grep --color=none -Ev "(\-\-\- a|\+\+\+ b|^.index)")
            //
            //        if [ "x$diffs" == "x" ] ; then
            //            $gitcmd reset HEAD "$patch" >/dev/null
            //            $gitcmd checkout -- "$patch" >/dev/null
            //        fi
        }
    }
}
