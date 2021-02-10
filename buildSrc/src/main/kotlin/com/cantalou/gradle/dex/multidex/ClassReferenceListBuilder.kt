/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.dx.rop.cst.ConstantPool
import com.android.dx.rop.cst.CstFieldRef
import com.android.dx.rop.cst.CstMethodRef
import com.android.dx.rop.cst.CstType
import com.android.dx.rop.type.Type
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.zip.ZipFile

/**
 * Tool to find direct class references to other classes.
 */
class ClassReferenceListBuilder(private val path: Path, combinedJar: String?) {
    val combinedJar: ZipFile
    val classNames: MutableSet<String> = LinkedHashSet()
    val recursiveClassNames = LinkedList<String>()

    /**
     * @param jarOfRoots Archive containing the class files resulting of the tracing, typically
     * this is the result of running ProGuard.
     */
    @Throws(IOException::class)
    fun addRoots(jarOfRoots: ZipFile) {
        // keep roots
        val entries = jarOfRoots.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryName = entry.name
            if (!entryName.endsWith(CLASS_EXTENSION)) {
                continue
            }
            //System.out.println("  " + entryName);
            recursiveClassNames.add(entryName)
            while (recursiveClassNames.size > 0) {
                val name = recursiveClassNames.removeFirst()
                if (classNames.contains(name)) {
                    continue
                }
                //System.out.println("      ");
                //System.out.println("      " + name);
                var classFile: DirectClassFile?
                classFile = try {
                    path.getClass(name)
                } catch (e: FileNotFoundException) {
                    throw IOException("Class $name is missing form original class path $path", e)
                }
                addDependencies(classFile.constantPool)
                classNames.add(name)
            }
        }
        combinedJar.close()
    }

    private fun addDependencies(pool: ConstantPool) {
        for (constant in pool.entries) {
            if (constant is CstType) {
                checkDescriptor(constant.classType)
            } else if (constant is CstFieldRef) {
                checkDescriptor(constant.type)
            } else if (constant is CstMethodRef) {
                val proto = constant.prototype
                checkDescriptor(proto.returnType)
                val args = proto.parameterTypes
                for (i in 0 until args.size()) {
                    checkDescriptor(args[i])
                }
            }
        }
    }

    private fun checkDescriptor(type: Type) {
        val descriptor = type.descriptor
        if (!descriptor.endsWith(";")) {
            return
        }
        val lastBrace = descriptor.lastIndexOf('[')
        if (lastBrace < 0) {
            // Lcom/sample/clazz;
            addClassWithHierarchy(descriptor.substring(1, descriptor.length - 1))
        } else {
            // array
            // [Lcom/sample/class;
            assert(descriptor.length > lastBrace + 3 && descriptor[lastBrace + 1] == 'L')
            addClassWithHierarchy(descriptor.substring(lastBrace + 2, descriptor.length - 1))
        }
    }

    private fun addClassWithHierarchy(className: String) {
        var className = className
        if (!className.endsWith(CLASS_EXTENSION)) {
            className = className + CLASS_EXTENSION
        }
        if (classNames.contains(className) || recursiveClassNames.contains(className)) {
            return
        }
        try {
            val classFile = path.getClass(className)
            recursiveClassNames.add(0, className)
            //System.out.println("            " + className);
            val superClass = classFile.superclass
            if (superClass != null) {
                val classType = superClass.classType
                addClassWithHierarchy(classType.className)
            }
            val interfaceList = classFile.interfaces
            val interfaceNumber = interfaceList.size()
            for (i in 0 until interfaceNumber) {
                val classType = interfaceList.getType(i)
                addClassWithHierarchy(classType.className)
            }
        } catch (e: FileNotFoundException) {
            // Ignore: The referenced type is not in the path it must be part of the libraries.
        }
    }

    companion object {
        private const val CLASS_EXTENSION = ".class"

        /**
         * Kept for compatibility with the gradle integration, this method just forwards to
         * [MainDexListBuilder.main].
         *
         */
        @Deprecated("use {@link MainDexListBuilder#main(String[])} instead.")
        @JvmStatic
        fun main(args: Array<String>) {
            MainDexListBuilder.Companion.main(args)
        }
    }

    init {
        this.combinedJar = ZipFile(combinedJar)
    }
}