/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cantalou.gradle.dex.multidex

import com.android.dx.cf.direct.DirectClassFile
import com.android.dx.cf.direct.StdAttributeFactory
import java.io.*
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipException
import java.util.zip.ZipFile

class Path(private val definition: String) {
    var elements: MutableList<ClassPathElement?> = ArrayList()
    private val byteArrayOutputStream = ByteArrayOutputStream(40 * 1024)
    private val readBuffer = ByteArray(20 * 1024)
    override fun toString(): String {
        return definition
    }

    fun getElements(): Iterable<ClassPathElement?> {
        return elements
    }

    private fun addElement(element: ClassPathElement?) {
        assert(element != null)
        elements.add(element)
    }

    @Synchronized
    @Throws(FileNotFoundException::class)
    fun getClass(path: String): DirectClassFile {
        var classFile: DirectClassFile? = null
        for (element in elements) {
            try {
                val input = element!!.open(path)
                try {
                    val bytes = readStream(input, byteArrayOutputStream, readBuffer)
                    byteArrayOutputStream.reset()
                    classFile = DirectClassFile(bytes, path, false)
                    classFile.setAttributeFactory(StdAttributeFactory.THE_ONE)
                    break
                } finally {
                    input.close()
                }
            } catch (e: IOException) {
                // search next element
            }
        }
        if (classFile == null) {
            throw FileNotFoundException("File \"$path\" not found")
        }
        return classFile
    }

    companion object {
        @Throws(ZipException::class, IOException::class)
        fun getClassPathElement(file: File): ClassPathElement {
            return if (file.isDirectory) {
                FolderPathElement(file)
            } else if (file.isFile) {
                ArchivePathElement(ZipFile(file))
            } else if (file.exists()) {
                throw IOException("\"" + file.path +
                        "\" is not a directory neither a zip file")
            } else {
                throw FileNotFoundException("File \"" + file.path + "\" not found")
            }
        }

        @Throws(IOException::class)
        private fun readStream(input: InputStream, baos: ByteArrayOutputStream, readBuffer: ByteArray): ByteArray {
            try {
                while (true) {
                    val amt = input.read(readBuffer)
                    if (amt < 0) {
                        break
                    }
                    baos.write(readBuffer, 0, amt)
                }
            } finally {
                input.close()
            }
            return baos.toByteArray()
        }
    }

    init {
        for (filePath in definition.split(Pattern.quote(File.pathSeparator)).toTypedArray()) {
            try {
                addElement(getClassPathElement(File(filePath)))
            } catch (e: IOException) {
                throw IOException("Wrong classpath: " + e.message, e)
            }
        }
    }
}