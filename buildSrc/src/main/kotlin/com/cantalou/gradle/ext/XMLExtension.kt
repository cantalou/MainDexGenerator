package com.cantalou.gradle.ext

import com.android.utils.forEach
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.Node.ELEMENT_NODE
import java.io.File
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 *
 * 操作XML文件的扩展
 *
 * @author  LinZhiWei
 * @date    2020年03月10日 16:43
 *
 * Copyright (c) 2020年, 4399 Network CO.ltd. All Rights Reserved.
 */

fun Node.createMetaData(name: String, value: Any) {
    val metaData = ownerDocument.createElement("meta-data")
    metaData.setAttribute("android:name", name)
    metaData.setAttribute("android:value", value.toString())
    appendChild(metaData)
}

/**
 * 在节点上添加Attribute
 */
fun Node.addAttr(name: String, value: String) {
    val attr = this.ownerDocument.createAttribute(name)
    attr.value = value
    attributes.setNamedItem(attr)
}

/**
 * 获取节点上名字为[name]的属性信息
 */
fun Node.attr(name: String): String {
    return attributes?.getNamedItem(name)?.textContent ?: ""
}

/**
 * 获取节点上名字为[name]的属性信息
 */
fun Node.attrNode(name: String): Node? {
    return attributes?.getNamedItem(name)
}

/**
 * 将内存中的xml信息写入文件中
 */
fun Document.write(dest: File) {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.transform(DOMSource(this), StreamResult(dest))
}

/**
 * 递归循环整个XML文件的节点
 */
fun Node.recursiveElement(action: (Node) -> Unit) {
    this.childNodes.forEach { childNode ->
        if (childNode.nodeType == ELEMENT_NODE && childNode.nodeName != null) {
            action(childNode)
        }
        childNode.recursiveElement(action)
    }
}