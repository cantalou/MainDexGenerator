package com.cantalou.gradle.plugin.plugins

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.dsl.model.ApiVersionImpl
import com.android.builder.core.DefaultApiVersion
import com.android.builder.dexing.DexingType
import com.android.builder.model.ApiVersion
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.sdklib.AndroidVersion
import com.cantalou.gradle.ext.android
import com.cantalou.gradle.ext.applicationVariants
import com.cantalou.gradle.ext.copyConfigurations
import com.cantalou.gradle.ext.plugin
import com.cantalou.gradle.plugin.extractor.ManifestExtractor
import com.cantalou.gradle.plugin.extractor.ManifestExtractorFactory
import com.cantalou.gradle.plugin.tasks.impl.*
import org.gradle.api.Project
import org.gradle.api.logging.Logging

class MainDexListPlugin : BasePlugin() {

    private lateinit var manifestExtractor: ManifestExtractor

    override fun apply(project: Project) {
        super.apply(project)
        manifestExtractor = ManifestExtractorFactory.createInstance(project.plugin.dependencyAndroidManifestDir)

        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
                if (project.android.defaultConfig.multiDexEnabled && variant.variantData.variantConfiguration.minSdkVersionWithTargetDeviceApi.isLegacyMultidex) {
                    println("Start create tasks for $variant")
                    createTasks(variant as ApplicationVariantImpl)
                }
            }
        }
    }

    private fun createTasks(variant: ApplicationVariantImpl) {

        //生成AndroidManifest.xml文件
        val taskNameSuffix = variant.name.capitalize()
        val hostExtractManifestTask = tasks.create("hostExtractManifestFor$taskNameSuffix", ExtractManifestTask::class.java)
        hostExtractManifestTask.config {
            it.variant = variant
            it.manifestExtractor = manifestExtractor
            it.configurations = project.copyConfigurations(variant.buildType.name)
        }

        //生成manifest-keep.txt文件
        val manifestKeepTask = tasks.create("manifestKeepFor$taskNameSuffix", ManifestKeepTask::class.java)
        manifestKeepTask.config {
            it.variant = variant
            it.extractManifestTask = hostExtractManifestTask
        }

        //生成包含项目所有class的jar文件
        var classJarTask = tasks.findByName("transformClassesAndResourcesWithProguardFor$taskNameSuffix")
        if (classJarTask == null) {
            classJarTask = tasks.create("mergeClassFor$taskNameSuffix", MergeClassTask::class.java)
            classJarTask.config {
                it.variant = variant
                it.outputPath = "${project.buildDir}/intermediates/transforms/jarMerge/${variant.dirName}/combine.jar"
            }
        }
        classJarTask!!.dependsOn(manifestKeepTask)
        classJarTask.outputs.upToDateWhen { false }

        //生成mainDexList.txt文件
        val mainDexListTask = tasks.create("mainDexListFor$taskNameSuffix", MainDexListTask::class.java)
        mainDexListTask.config {
            it.variant = variant
            it.jarTask = classJarTask
        }
        mainDexListTask.dependsOn(classJarTask)

        //计算每个class的方法数
        val methodCountTask = tasks.create("methodCountFor$taskNameSuffix", MethodCountTask::class.java)
        methodCountTask.config {
            it.mainDexListTask = mainDexListTask
        }

        //根据自定义方法数 和 mainDexList.txt 将jar拆分成多个jar文件
        val splitByMethodTask = tasks.create("splitByMethodFor$taskNameSuffix", SplitJarTask::class.java)
        splitByMethodTask.config {
            it.variant = variant
            it.jarTask = classJarTask;
            it.mainDexListTask = mainDexListTask
            it.methodCountTask = methodCountTask
        }

        //将jar文件编译成dex文件
        val dexBuildTransform = DexBuilderTransform(project)
        dexBuildTransform.config {
            it.variant = variant
            it.splitByMethodTask = splitByMethodTask;
        }
    }
}


















