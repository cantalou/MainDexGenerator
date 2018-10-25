package com.cantalou.gradle.dex

import com.android.build.gradle.api.ApplicationVariant
import com.cantalou.gradle.dex.tasks.CreateManifestKeepTask
import com.cantalou.gradle.dex.tramsform.CustomMainDexTransform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

/**
 *
 * 1.定义CreateManifestKeepTask用于生成manifest_keep.txt文件
 * 2.替换MultiDexTransform, 生成mainDexListFile.txt文件
 *
 * @author LinZhiWei
 * @date 2018年09月17日 16:30
 *
 * Copyright (c) 2018年, 4399 Network CO.ltd. All Rights Reserved.
 */
class MainDexListPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def configurations = [debug: copyConfigurations(project, "debug"), release: copyConfigurations(project, "release")]

        def originalAppManifestFile = project.android.sourceSets.main.manifest.srcFile

        project.afterEvaluate {
            project.android.applicationVariants.each { ApplicationVariant variant ->

                def capitalizeName = variant.name.capitalize()

                CreateManifestKeepTask createManifestKeepTask = project.tasks.create("createNewManifestKeep${capitalizeName}", CreateManifestKeepTask.class)
                createManifestKeepTask.addManifest(originalAppManifestFile)
                createManifestKeepTask.configurations = configurations[variant.buildType.name]
                createManifestKeepTask.outputFile = variant.variantData.scope.getManifestKeepListProguardFile()
                createManifestKeepTask.variantName = variant.name

                Task jarMergeTask = project.tasks.findByName("transformClassesWithJarMergingFor${capitalizeName}")
                if (jarMergeTask != null) {
                    createManifestKeepTask.dependsOn jarMergeTask
                }

                Task proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${capitalizeName}")
                if (proguardTask != null) {
                    createManifestKeepTask.dependsOn proguardTask
                }

                def transformMultiDexListTask = project.tasks.findByName("transformClassesWithMultidexlistFor${capitalizeName}")
                CustomMainDexTransform.inject(transformMultiDexListTask)
                transformMultiDexListTask.dependsOn createManifestKeepTask

                ["collect${capitalizeName}MultiDexComponents", "createManifestKeep${capitalizeName}"].each {
                    Task task = project.tasks.findByName(it)
                    if (task != null) {
                        task.enabled = false
                        project.println "disable task ${task}"
                    }
                }
            }
        }
    }

    def copyConfigurations(def project, def buildType) {
        def configurations = []
        def projectConfigurations = project.configurations
        ["compile", "api", "implementation"].each {
            Configuration configuration = projectConfigurations.findByName(it)
            if (configuration != null) {
                configurations << configuration.copy()
            }
            configuration = projectConfigurations.findByName("${buildType}${it.capitalize()}")
            if (configuration != null) {
                configurations << configuration.copy()
            }
        }
        configurations
    }
}
