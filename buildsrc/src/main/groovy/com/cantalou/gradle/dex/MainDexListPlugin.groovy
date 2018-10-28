package com.cantalou.gradle.dex

import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.dsl.DexOptions
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.DexMergerTransform
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.builder.model.Version
import com.cantalou.gradle.dex.tasks.CreateManifestKeepTask
import com.cantalou.gradle.dex.tramsform.CustomMainDexTransform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

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

    def configurations

    @Override
    void apply(Project project) {

        println Version.ANDROID_GRADLE_PLUGIN_VERSION

        createTask(project)
    }

    void createTask(Project project) {
        def originalAppManifestFile = project.android.sourceSets.main.manifest.srcFile

        project.android.applicationVariants.all { ApplicationVariant variant ->

            VariantScope variantScope = variant.variantData.scope
            if (!variantScope.getNeedsMainDexList()) {
                return
            }

            def capitalizeName = variant.name.capitalize()

            //legacy_multidex_main_dex_list
            def outputDir = new File(project.buildDir.absolutePath + "/" + FD_INTERMEDIATES + "/multi-dex/" + variant.dirName)
            def manifestKeepFile = new File(outputDir, "manifest_keep.txt")
            manifestKeepFile.parentFile.mkdirs()

            CreateManifestKeepTask createManifestKeepTask = project.tasks.create("createNewManifestKeep${capitalizeName}", CreateManifestKeepTask.class)
            createManifestKeepTask.variantScope = variantScope
            createManifestKeepTask.addManifest(originalAppManifestFile)
            createManifestKeepTask.configurations = copyConfigurations(project, variant.buildType.name)
            createManifestKeepTask.outputFile = manifestKeepFile

            Task jarMergeTask = project.tasks.findByName("transformClassesWithJarMergingFor${capitalizeName}")
            if (jarMergeTask != null) {
                createManifestKeepTask.dependsOn jarMergeTask
            }

            Task proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${capitalizeName}")
            if (proguardTask != null) {
                createManifestKeepTask.dependsOn proguardTask
            }

            def multiDexListTask = project.tasks.findByName("transformClassesWithMultidexlistFor${capitalizeName}")
            multiDexListTask.dependsOn createManifestKeepTask

            //DexMergerTransform, DexTransform
            def mainDexListFile = new File(outputDir, "maindexlist.txt")
            def dexTask = project.tasks.findByName("transformDexArchiveWithDexMergerFor${capitalizeName}")
            if(dexTask == null){
                dexTask = project.tasks.findByName("transformDexArchiveWithDexMergerFor${capitalizeName}")
            }
            Transform dexTransform = dexTask.transform
            dexTransform.getSecondaryFiles().each { secondaryFile ->
                mainDexListFile = secondaryFile.getFileCollection(project).singleFile
            }

            Transform mainDexTransform = multiDexListTask.transform
            DexOptions dexOptions = variantScope.getGlobalScope().getExtension().getDexOptions()
            CustomMainDexTransform customMainDexTransform = new CustomMainDexTransform(variantScope, dexOptions, null)
            customMainDexTransform.variantScope = variantScope
            customMainDexTransform.outputDir = outputDir
            customMainDexTransform.manifestKeepListFile = manifestKeepFile
            customMainDexTransform.mainDexListFile = mainDexListFile
            setValue(multiDexListTask, TransformTask.class, "transform", customMainDexTransform)
            println "change mainDexTransform for " + multiDexListTask.toString() + " from " + mainDexTransform + " to " + customMainDexTransform


            ["collect${capitalizeName}MultiDexComponents", "createManifestKeep${capitalizeName}"].each {
                Task task = project.tasks.findByName(it)
                if (task != null) {
                    task.enabled = false
                    project.println "disable task ${task}"
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

    static def getValue(Object instance, Class clazz, String fieldName) throws IllegalAccessException {
        getField(clazz, fieldName, true).get(instance)
    }

    static void setValue(Object instance, Class clazz, String fieldName, Object value) {
        Field field = getField(clazz, fieldName, true)
        field.set(instance, value)
    }

    static def getField(Class cls, String fieldName, boolean forceAccess) {
        if (cls == null) {
            throw new IllegalArgumentException("The class must not be null")
        } else if (fieldName == null) {
            throw new IllegalArgumentException("The field name must not be null")
        } else {
            for (Class clazz = cls; clazz != null; clazz = clazz.getSuperclass()) {
                try {
                    Field field = clazz.getDeclaredField(fieldName)
                    if (!Modifier.isPublic(field.getModifiers())) {
                        if (!forceAccess) {
                            continue
                        }
                        field.setAccessible(true);
                    }
                    return field
                } catch (NoSuchFieldException var7) {
                }
            }
            throw new NoSuchFieldException(fieldName)
        }
    }
}
