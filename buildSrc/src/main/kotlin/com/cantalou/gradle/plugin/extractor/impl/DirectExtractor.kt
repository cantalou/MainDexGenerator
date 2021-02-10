package com.cantalou.gradle.plugin.extractor.impl

import com.android.SdkConstants
import com.cantalou.gradle.plugin.extractor.ManifestExtractor
import com.cantalou.gradle.ext.android
import com.cantalou.gradle.ext.copyConfigurations
import com.cantalou.gradle.ext.main
import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs


class DirectExtractor(outputDir: File) : ManifestExtractor(outputDir) {

    override fun extract(project: Project, vararg buildTypes: String): MutableSet<File> {
        val configurations = project.copyConfigurations(*buildTypes)
        return extract(configurations)
    }

    /**
     * 解析AAR依赖中的AndroidManifest.xml文件, 复制到缓存目录
     */
    override fun extract(configurations: List<Configuration>): MutableSet<File> {
        val dependencyManifest = HashSet<File>()
        configurations.forEach configuration@{ configuration ->
            if(!configuration.isCanBeResolved){
                configuration.isCanBeResolved = true
            }

            configuration.dependencies.filter { it is ProjectDependency && it.dependencyProject.hasProperty("android") }.forEach {
                configuration.dependencies.remove(it)
                dependencyManifest.add((it as ProjectDependency).dependencyProject.android.sourceSets.main.manifest.srcFile)
            }

            configuration.files.filter { it.name.endsWith(".aar") }.forEach aarFile@{ arrFile ->
                val destDir = File(outputDir, "${arrFile.name}-${abs(arrFile.hashCode())}")
                destDir.mkdirs()
                val manifestFile = File(destDir, SdkConstants.ANDROID_MANIFEST_XML)
                if (manifestFile.exists()) {
                    dependencyManifest.add(manifestFile)
                    return@aarFile
                }
                ZipFile(arrFile).use { zipFile ->
                    val entry = zipFile.getEntry(SdkConstants.ANDROID_MANIFEST_XML)
                    manifestFile.outputStream().use { destOutputStream ->
                        zipFile.getInputStream(entry).use { entryInputStream ->
                            IOUtils.copy(entryInputStream, destOutputStream)
                        }
                    }
                }
                dependencyManifest.add(manifestFile)
            }
        }
        return dependencyManifest
    }

}