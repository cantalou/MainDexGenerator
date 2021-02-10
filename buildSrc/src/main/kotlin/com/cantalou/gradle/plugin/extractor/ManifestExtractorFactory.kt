package com.cantalou.gradle.plugin.extractor

import com.cantalou.gradle.plugin.extractor.impl.DirectExtractor
import java.io.File


object ManifestExtractorFactory {

    fun createInstance(outputDir: File): ManifestExtractor {
        return DirectExtractor(outputDir)
    }
}