package com.cantalou.gradle.plugin.tasks.impl

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.tasks.D8MainDexListTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.cantalou.gradle.TaskConfig
import com.cantalou.gradle.dex.multidex.MainDexListBuilder
import com.cantalou.gradle.ext.get
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import proguard.Configuration
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


open class MainDexListTask : DefaultTask(), TaskConfig<MainDexListTask> {

    lateinit var variant: ApplicationVariantImpl

    lateinit var jarTask: Task

    lateinit var d8MainDexListTask: D8MainDexListTask

    lateinit var processResourceTask: LinkApplicationAndroidResourcesTask

    var manifestKeepFile: File? = null

    var allClassesJar: File? = null

    var componentJar: File? = null

    @OutputFile
    lateinit var mainDexListFile: File

    lateinit var dexBuildTask: TransformTask

    override fun config(action: (MainDexListTask) -> Unit) {
        action(this)
        processResourceTask = variant.variantData.taskContainer.processAndroidResTask!!.get() as LinkApplicationAndroidResourcesTask
        manifestKeepFile = processResourceTask.mainDexListProguardOutputFile

        d8MainDexListTask = project.tasks.findByName("multiDexList${variant.name.capitalize()}") as D8MainDexListTask
        d8MainDexListTask.dependsOn(this)

        mainDexListFile = d8MainDexListTask.output.get().asFile

        dexBuildTask = project.tasks.findByName("transformClassesWithDexBuilderFor${variant.name.capitalize()}") as TransformTask
        dexBuildTask.dependsOn(this)

        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {

        d8MainDexListTask?.let {
            d8MainDexListTask.enabled = false
        }

        if (allClassesJar == null) {
            allClassesJar = getAllClassesJarFile(jarTask)!!
        }

        if (componentJar == null) {
            componentJar = generateComponentJar(allClassesJar!!)
        }

        val mainDexClasses = computeList(allClassesJar!!, componentJar!!)
        val fileContent = mainDexClasses.joinToString(System.getProperty("line.separator"))

        mainDexListFile.parentFile.mkdirs()
        mainDexListFile.writeText(fileContent)

    }

    private fun generateComponentJar(allClassesJarFile: File): File {
        val componentClasses = manifestKeepFile!!.readLines()
        val componentJar = File(allClassesJarFile.parentFile, "component.jar")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(componentJar))).use { outZip ->
            ZipFile(allClassesJarFile).use { allZipFile ->
                allZipFile.entries().iterator().forEach classItem@{ fromEntry ->

                    val name = fromEntry.name
                    if (!name.endsWith(".class")) {
                        return@classItem
                    }

                    var endIndex = name.indexOf('$')
                    if (endIndex == -1) {
                        endIndex = name.lastIndexOf('.')
                    }
                    val classNamePrefix = name.substring(0, endIndex)
                    if (componentClasses.contains(classNamePrefix)) {
                        allZipFile.getInputStream(fromEntry).use { fromIS ->
                            val newEntry = ZipEntry(name)
                            outZip.putNextEntry(newEntry)
                            IOUtils.copy(fromIS, outZip)
                        }
                    }
                }
            }
        }
        return componentJar
    }

    private fun getAllClassesJarFile(jarTask: Task): File? {
        if (jarTask is MergeClassTask) {
            return File(jarTask.outputPath)
        } else {
            val proguardTransform = ((jarTask as TransformTask).transform as ProGuardTransform)
            val configuration: Configuration = proguardTransform.get("configuration") as Configuration
            for (index in 0..configuration.programJars.size()) {
                val cpe = configuration.programJars[index]
                if (cpe.isOutput) {
                    return cpe.file
                }
            }
        }
        return null
    }

    private fun computeList(allClassesJarFile: File, jarOfRoots: File): Set<String> {
        val args = arrayOf("--disable-annotation-resolution-workaround", jarOfRoots.absolutePath, allClassesJarFile.absolutePath)
        MainDexListBuilder.main(args)
        return MainDexListBuilder.mainDexList
    }
}