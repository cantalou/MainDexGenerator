package com.cantalou.gradle.plugin.extractor.impl

import com.cantalou.gradle.plugin.extractor.ManifestExtractor
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File


class BuildCacheExtractor(outputDir: File) : ManifestExtractor(outputDir) {

    override fun extract(project: Project, vararg buildTypes: String): MutableSet<File> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun extract(configurations: List<Configuration>): MutableSet<File> {
        TODO("Not yet implemented")
    }
}