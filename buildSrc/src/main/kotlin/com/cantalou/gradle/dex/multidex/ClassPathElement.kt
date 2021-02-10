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

import java.io.IOException
import java.io.InputStream

/**
 * An element of the class path in which class files can be found.
 */
interface ClassPathElement {
    /**
     * Open a "file" from this `ClassPathElement`.
     * @param path a '/' separated relative path to the wanted file.
     * @return an `InputStream` ready to read the requested file.
     * @throws IOException if the path can not be found or if an error occurred while opening it.
     */
    @Throws(IOException::class)
    fun open(path: String): InputStream

    @Throws(IOException::class)
    fun close()
    fun list(): Iterable<String>

    companion object {
        const val SEPARATOR_CHAR = '/'
    }
}