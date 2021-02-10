package com.cantalou.gradle.ext

import org.w3c.dom.Document
import java.io.File
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 读取字符串路径指定的文件内容
 */
fun String.fileContent(charset: Charset = Charsets.UTF_8): String {
    return File(this).readText(charset)
}

/**
 * 解析xml
 */
fun File.parseXml(): Document {
    val newInstance = DocumentBuilderFactory.newInstance()
    newInstance.isIgnoringComments = true
    val manifestDoc = newInstance.newDocumentBuilder().parse(this)
    manifestDoc.documentElement.normalize()
    return manifestDoc
}

/**
 * 解析字符串路径指定的xml文件
 */
fun String.parseXmlFile(): Document {
    return File(this).parseXml()
}

/**
 * 判断字符串路径指定的文件是否存在
 */
fun String.exists(): Boolean {
    return File(this).exists()
}