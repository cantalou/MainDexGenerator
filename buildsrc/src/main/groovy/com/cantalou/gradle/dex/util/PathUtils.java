package com.cantalou.gradle.dex.util;

import com.android.annotations.NonNull;

import java.nio.file.Path;

/**
 * Utility methods for {@link java.nio.file.Path}.
 */
public class PathUtils
{

    /** Returns a system-independent path. */
    @NonNull
    public static String toSystemIndependentPath(@NonNull Path path) {
        String filePath = path.toString();
        if (!path.getFileSystem().getSeparator().equals("/")) {
            return filePath.replace(path.getFileSystem().getSeparator(), "/");
        }
        return filePath;
    }
}
