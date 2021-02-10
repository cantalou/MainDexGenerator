package com.cantalou.gradle.plugin.tasks.impl

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.packaging.JarMerger
import com.cantalou.gradle.TaskConfig
import com.cantalou.gradle.ext.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File


open class ManifestKeepTask : DefaultTask(), TaskConfig<ManifestKeepTask> {

    lateinit var variant: ApplicationVariantImpl

    lateinit var extractManifestTask: ExtractManifestTask

    lateinit var processResourceTask: LinkApplicationAndroidResourcesTask

    override fun config(action: (ManifestKeepTask) -> Unit) {
        action(this)
        processResourceTask = variant.variantData.taskContainer.processAndroidResTask!!.get() as LinkApplicationAndroidResourcesTask
        dependsOn(processResourceTask)
        dependsOn(extractManifestTask)
    }

    @TaskAction
    fun generate() {
        var allManifestFile = extractManifestTask.extractedManifestFiles
        if (allManifestFile == null) {
            allManifestFile = mutableSetOf<File>()
        }
        allManifestFile.add(project.android.sourceSets.main.manifest.srcFile)

        var packageName = ""
        val rootElementName = setOf("application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation")

        var manifestKeepFile = processResourceTask.mainDexListProguardOutputFile
        if (manifestKeepFile == null) {
            manifestKeepFile = File(project.buildDir, "intermediates/${InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES}")
        }
        manifestKeepFile.parentFile.mkdirs()
        manifestKeepFile.printWriter().use { writer ->
            allManifestFile.forEach { xmlFile ->
                xmlFile.parseXml().recursiveElement { node ->
                    if (node.nodeName == "manifest") {
                        packageName = node.attr("package")
                    }

                    if (rootElementName.contains(node.nodeName)) {
                        var className = node.attr("android:name")
                        if (className.isEmpty()) {
                            className = node.attr("name")
                        }
                        if (className.startsWith(".")) {
                            className = packageName + className
                        }

                        if (className.contains("$")) {
                            className = className.substring(0, className.indexOf("$"))
                        }

                        className = className.replace(".","/")

                        if (className.isNotEmpty()) {
                            writer.println(className)
                        }
                    }
                }
            }

        }
    }
}