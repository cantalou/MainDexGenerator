package com.cantalou.gradle.plugin.plugins

import com.cantalou.gradle.plugin.extension.PluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.slf4j.Logger

open class BasePlugin : Plugin<Project> {

    lateinit var project: Project

    lateinit var tasks: TaskContainer

    lateinit var pluginExtension: PluginExtension

    lateinit var logger: Logger

    override fun apply(project: Project) {
        this.project = project
        tasks = project.tasks
        pluginExtension = PluginExtension.add(project)
        logger = project.logger
        logger.info("apply plugin $this")
    }
}