package com.cantalou.gradle.dex.tasks

import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML

/**
 * Custom implement to create manifest_keep.txt file
 *
 * @author cantalou
 * @date 2018/09/18 17:45
 *
 * */
class CreateManifestKeepTask extends DefaultTask {

    Set<File> manifestSources = new HashSet<>()

    File outputFile

    List<Configuration> configurations

    VariantScope variantScope

    private static final Set<String> KEEP_SPECS = ["application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation"] as Set

    void addManifest(File from) {
        manifestSources.add(from)
        println "add AndroidManifest.xml from ${from}"
    }

    @InputFiles
    Set<File> getSources() {
        configurations.each { Configuration configuration ->
            manifestSources.addAll(configuration.getFiles().findAll { it.name.endsWith(".aar") })
            configuration.getDependencies().findAll {
                it instanceof ProjectDependency && it.dependencyProject.hasProperty("android")
            }.each {
                manifestSources << it.dependencyProject.android.sourceSets.main.manifest.srcFile
            }
        }
        return manifestSources
    }

    @OutputFile
    File getOutputFile() {
        return outputFile
    }

    void setConfigurations(List<Configuration> configurations) {
        this.configurations = configurations
    }

    @TaskAction
    void generateKeepListFromManifest() throws ParserConfigurationException, SAXException, IOException {
        Project project = variantScope.globalScope.project
        File cachedDir = new File("${project.buildDir}/intermediates/exploded-aar/manifest")
        cachedDir.mkdirs()
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser()
        outputFile.withWriter { Writer out ->
            getSources().collect { File source ->
                if (source.name.endsWith(".aar")) {
                    File destDir = new File(cachedDir, source.name + "." + Math.abs(source.hashCode()))
                    destDir.mkdirs()
                    def manifestFile = new File(destDir, FN_ANDROID_MANIFEST_XML)
                    if (!manifestFile.exists()) {
                        project.copy {
                            from project.zipTree(source)
                            include FN_ANDROID_MANIFEST_XML
                            into destDir
                        }
                    }
                    return manifestFile
                } else {
                    return source
                }
            }.each { File manifestFile ->
                println "Starting to parse ${manifestFile}"
                parser.parse(manifestFile, new ManifestHandler(out))
            }
        }
    }

    private static class ManifestHandler extends DefaultHandler {

        def packageName

        Writer out

        ManifestHandler(Writer out) {
            this.out = out
        }

        @Override
        void startElement(String uri, String localName, String qName, Attributes attr) {

            if (qName == "manifest") {
                packageName = attr.getValue("package")
            }

            if (KEEP_SPECS.contains(qName)) {
                keepClass(attr.getValue("android:name"))
                // Also keep the original application class when using instant-run.
                keepClass(attr.getValue("name"))
            }
        }

        void keepClass(String className) {
            if (className != null) {
                if (className.startsWith(".")) {
                    className = packageName + className
                }
                try {
                    out.write(className.replace('.', '/'))
                    out.write("\n")
                }
                catch (IOException e) {
                    throw new RuntimeException(e)
                }
            }
        }
    }


}