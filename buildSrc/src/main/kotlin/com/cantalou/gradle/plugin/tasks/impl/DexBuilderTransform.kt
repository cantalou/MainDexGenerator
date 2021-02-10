package com.cantalou.gradle.plugin.tasks.impl

import com.android.build.api.transform.*
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform
import com.android.builder.dexing.DexingType
import com.cantalou.gradle.TaskConfig
import com.cantalou.gradle.ext.set
import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile


open class DexBuilderTransform(val project: Project) : Transform(), TaskConfig<DexBuilderTransform> {

    lateinit var variant: ApplicationVariantImpl

    lateinit var splitByMethodTask: SplitJarTask

    lateinit var outputDir: File

    lateinit var target: DexArchiveBuilderTransform

    lateinit var transformDexTask: TransformTask

    override fun config(action: (DexBuilderTransform) -> Unit) {
        action(this)

        val tasks = project.tasks
        val variantName = variant.name.capitalize()

        val dexOutputDir = when (variant.variantData.scope.dexingType) {
            DexingType.LEGACY_MULTIDEX -> "mergeDex$variantName"
            else -> "mergeProjectDex$variantName"
        }
        outputDir = File(project.buildDir, "intermediates/dex/${variant.name}/$dexOutputDir/out")

        //将class文件编译成dex文件 .class -> .dex, .jar -> .dex
        transformDexTask = tasks.getByName("transformClassesWithDexBuilderFor$variantName") as TransformTask
        transformDexTask.dependsOn(splitByMethodTask)
        target = transformDexTask.transform as DexArchiveBuilderTransform
        target.set("numberOfBuckets", 1)
        transformDexTask.set("transform", this)

        //将多个.dex文件进行合并, 并根据mainDexList.txt的内容筛选classes.dex文件的class
        arrayOf("mergeDex", "mergeProjectDex", "mergeExtDex", "mergeLibDex").forEach { prefix ->
            tasks.findByName("$prefix$variantName")?.enabled = false
        }
    }

    override fun transform(transformInvocation: TransformInvocation?) {

        //将transformClassesWithDexBuilder任务的编译源文件
        target.transform(object : TransformInvocation {
            override fun getInputs(): MutableCollection<TransformInput> {

                val jarInputs = mutableListOf<JarInput>()
                //linux系统获取的文件列表没有按名称排序
                splitByMethodTask.jarOutputDir.listFiles().asList().sortedBy {
                    it.name
                }.forEach { jarFile ->
                    println("add jarFile $jarFile to transformClassesWithDexBuilder task")
                    jarInputs.add(object : JarInput, Serializable {
                        override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
                            return mutableSetOf(QualifiedContent.Scope.PROJECT)
                        }

                        override fun getStatus(): Status {
                            return Status.ADDED
                        }

                        override fun getFile(): File {
                            return jarFile
                        }

                        override fun getName(): String {
                            return jarFile.name
                        }

                        override fun getContentTypes(): MutableSet<QualifiedContent.ContentType> {
                            return mutableSetOf(QualifiedContent.DefaultContentType.CLASSES)
                        }
                    })
                }

                val inputs = mutableListOf<TransformInput>()
                inputs.add(object : TransformInput, Serializable {
                    override fun getDirectoryInputs(): MutableCollection<DirectoryInput> {
                        return mutableListOf<DirectoryInput>()
                    }
                    override fun getJarInputs(): MutableCollection<JarInput> {
                        return jarInputs
                    }
                })
                return inputs
            }

            override fun getSecondaryInputs(): MutableCollection<SecondaryInput> {
                return transformInvocation!!.secondaryInputs
            }

            override fun getReferencedInputs(): MutableCollection<TransformInput> {
                return transformInvocation!!.referencedInputs
            }

            override fun isIncremental(): Boolean {
                return false
            }

            override fun getOutputProvider(): TransformOutputProvider {
                return transformInvocation!!.outputProvider
            }

            override fun getContext(): Context {
                return transformInvocation!!.context
            }

        })

        outputDir.mkdirs()

        transformDexTask.outputs.files.flatMap {
            it.listFiles { _, name -> name!!.endsWith(".jar") }.toList()
        }.forEach { dexZip ->
            val index = dexZip.name.substring(0, dexZip.name.indexOf(".")).toInt()
            ZipFile(dexZip).use { zipFile ->
                val dexEntry = zipFile.getEntry("classes.dex")
                zipFile.getInputStream(dexEntry).use { from ->
                    val dexDexFile = File(outputDir, "classes${when (index) {
                        0 -> ""
                        else -> (index + 1).toString()
                    }}.dex")
                    println("unzip classes.dex from $dexZip to $dexDexFile")
                    dexDexFile.outputStream().use { output ->
                        IOUtils.copy(from, output)
                    }
                }
            }
        }

    }

    override fun getName(): String {
        return target.name
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return target.inputTypes
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return target.scopes
    }

}