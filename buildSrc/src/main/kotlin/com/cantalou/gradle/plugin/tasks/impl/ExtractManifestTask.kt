package com.cantalou.gradle.plugin.tasks.impl

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.cantalou.gradle.TaskConfig
import com.cantalou.gradle.ext.*
import com.cantalou.gradle.plugin.extractor.ManifestExtractor
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File


open class ExtractManifestTask : DefaultTask(), TaskConfig<ExtractManifestTask> {

    var pluginProjects: List<Project>? = null

    var configurations: List<Configuration>? = null

    lateinit var manifestExtractor: ManifestExtractor

    lateinit var variant: ApplicationVariantImpl

    var extractedManifestFiles: MutableSet<File>? = null
        get() {
            if (field == null && extractedList.exists()) {
                field = extractedList.readLines().map { File(it) }.toMutableSet()
            }
            return field
        }

    @OutputFile
    lateinit var extractedList: File

    override fun config(action: (ExtractManifestTask) -> Unit) {
        action(this)
        pluginProjects?.let {
            for (pluginProject in it) {
                inputs.file(File(pluginProject.projectDir, "build.gradle").absolutePath)
            }
        }

        outputs.dir(manifestExtractor.outputDir)
        dependsOn(variant.variantData.taskContainer.mergeResourcesTask.get())

        extractedList = File(project.plugin.dependencyAndroidManifestDir, "${name}.txt")
        if (!extractedList.exists()) {
            outputs.upToDateWhen { false }
        }
    }

    @TaskAction
    fun extract() {
        pluginProjects?.let {
            extractedManifestFiles = manifestExtractor.extract(it, variant.buildType.name)
        }

        configurations?.let {
            extractedManifestFiles = manifestExtractor.extract(it)
        }
        extractedList.printWriter().use { writer ->
            extractedManifestFiles!!.forEach { file ->
                writer.println(file.absolutePath)
            }
        }
    }
}