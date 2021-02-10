package com.cantalou.gradle.plugin.extractor

import com.google.common.collect.Multiset
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File

abstract class ManifestExtractor(val outputDir: File) {

    /**
     * 解析[project]项目中的依赖信息, 并将AAR类型依赖的AndroidManifest.xml文件释放到[outputDir]目录, 返回所有解压出来的文件集合
     */
    abstract fun extract(project: Project, vararg buildTypes: String): MutableSet<File>

    /**
     * 解析[projects]中所有项目中的依赖信息
     */
    fun extract(projects: Iterable<Project>, vararg buildTypes: String): MutableSet<File> {
        return projects.flatMap { extract(it, *buildTypes) }.toMutableSet()
    }

    /**
     * 获取指定项目的Configuration的外部依赖AAR中AndroidManifest.xml
     */
    abstract fun extract(configurations: List<Configuration>): MutableSet<File>
}