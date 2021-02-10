package com.cantalou.gradle.ext

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.builder.internal.aapt.AaptOptions
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.google.common.collect.ImmutableMap
import com.cantalou.gradle.plugin.extension.PluginExtension
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.internal.extensibility.DefaultExtraPropertiesExtension
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 *
 * Android Gradle Plugin 扩展
 *
 * @author  LinZhiWei
 * @date    2020年01月02日 11:22
 *
 * Copyright (c) 2020年, 4399 Network CO.ltd. All Rights Reserved.
 */

/**
 * 返回引入的 "com.android.application" 或者 "com.android.library" 插件的扩展对象, 这个对象不包含 "applicationVariant" 和 "libraryVariant"
 */
val Project.android: BaseExtension
    get() = property("android") as BaseExtension


val BaseExtension.applicationVariants: DomainObjectSet<ApplicationVariantImpl>
    @SuppressWarnings("unchecked")
    get() {
        return ((this as AppExtension).applicationVariants) as DomainObjectSet<ApplicationVariantImpl>
    }

val BaseExtension.libraryVariants: DomainObjectSet<LibraryVariantImpl>
    @SuppressWarnings("unchecked")
    get() {
        return (this as LibraryExtension).libraryVariants as DomainObjectSet<LibraryVariantImpl>
    }

/**
 * Project 引入本地插件
 */
fun Project.apply(vararg classes: Class<*>) {
    classes.forEach {
        apply(ImmutableMap.of<String, Any>("plugin", it))
    }
}

/**
 * Project 根据Class引入本地插件
 */
fun Project.apply(vararg classes: String) {
    classes.forEach {
        apply(ImmutableMap.of<String, Any>("plugin", it))
    }
}

/**
 * Project 根据uri引入插件
 */
fun Project.from(vararg classes: String) {
    classes.forEach {
        apply(ImmutableMap.of<String, Any>("from", it))
    }
}

val NamedDomainObjectContainer<AndroidSourceSet>.main: AndroidSourceSet
    get() = getByName("main")

val Project.plugin: PluginExtension
    get() = project.extensions.getByName("plugin") as PluginExtension

val ProcessAndroidResources.aaptOptions: AaptOptions
    get() {
        //this为LinkApplicationAndroidResourcesTask 动态代理后生成的子类 LinkApplicationAndroidResourcesTask_Decorated 调用superclass才是正确的Class
        val field = javaClass.superclass.getDeclaredField("aaptOptions")
        field.isAccessible = true
        return field.get(this) as AaptOptions
    }

fun AaptOptions.additionalParameter(vararg parameter: String) {
    if (additionalParameters == null) {
        val field = javaClass.getDeclaredField("additionalParameters")
        field.isAccessible = true
        field.set(this, ArrayList<String>())
    }
    (additionalParameters as MutableList<String>).addAll(parameter)
}

/**
 * 判断当前正常运行的任务是否为 "createBaseTaskRelease"
 */
fun Project.isExecCreateBaseTask(): Boolean {
    return this.gradle.startParameter.taskNames.any { it.contains("createBaseTaskRelease") }
}

/**
 * 判断当前正常运行的任务是否为 "assembleRelease/Debug"
 */
fun Project.isExecPluginPackage(): Boolean {
    return this.gradle.startParameter.taskNames.any { it.matches(Regex("(:.*?:)?assemblePlugin(Debug|Release)")) }
}

/**
 * 判断是否内置的插件, 比如: main 主插件
 */
fun Project.isNestedPlugin(): Boolean {
    return project.plugin.nestedPlugins.contains(project.path)
}

/**
 * 判断是否内置的插件, 比如: plugin_jfq 插件
 */
fun Project.isExternalPlugin(): Boolean {
    return project.plugin.externalPlugins.contains(project.path)
}


/**
 *读取项目根目录 local.properties 里面的值
 */
fun Project.localProperty(key: String): String? {
    val ext = project.findProperty("ext") as DefaultExtraPropertiesExtension
    var prop = ext.find("local.properties") as Properties?
    if (prop == null || prop.isEmpty) {
        prop = Properties()
        val propertiesFile = File(rootDir, "local.properties")
        propertiesFile.inputStream().use {
            prop.load(it)
        }
        ext.set("local.properties", prop)
    }
    return prop.getProperty(key)
}


/**
 * 复制项目所有的依赖配置
 */
fun Project.copyConfigurations(vararg buildTypes: String): ArrayList<Configuration> {
    var configurations = ArrayList<Configuration>()
    var projectConfigurations = this.configurations
    arrayOf("compile", "api", "implementation").forEach {
        var configuration = projectConfigurations.findByName(it) as Configuration
        configurations.add(configuration.copy())
        buildTypes.forEach { buildType ->
            configuration = (projectConfigurations.findByName("${buildType}${it.capitalize()}") as Configuration).copy()
            println("copyConfigurations from $configuration" )
            configurations.add(configuration)
            configuration.dependencies.filterIsInstance<ProjectDependency>().forEach {
                configurations.addAll(it.dependencyProject.copyConfigurations(*buildTypes))
            }
        }
    }
    return configurations
}

fun LibraryVariantImpl.getVariantData(): LibraryVariantData {
    return get("variantData") as LibraryVariantData
}

