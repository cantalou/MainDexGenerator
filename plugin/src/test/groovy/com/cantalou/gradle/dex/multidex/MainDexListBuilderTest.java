package com.cantalou.gradle.dex.multidex;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author cantalou
 * @date 2018年09月18日 13:28
 */
public class MainDexListBuilderTest {

    private File jarAllPath;

    private File jarRootPath;

    @Before
    public void setUp() {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("jarAll.jar");
        jarAllPath = new File(resource.getPath());

        resource = classLoader.getResource("jarRoot.jar");
        jarRootPath = new File(resource.getPath());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void main() {
        assertTrue(jarRootPath.exists());
        assertTrue(jarAllPath.exists());

        String[] args = new String[]{jarRootPath.getAbsolutePath(), jarAllPath.getAbsolutePath()};
        MainDexListBuilder.main(args);
        Set<String> classList = MainDexListBuilder.getMainDexList();
        assertEquals(9, classList.size());
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/Path.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/MainDexListBuilder.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/FolderPathElement.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/ClassReferenceListBuilder.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/ClassPathElement.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/ArchivePathElement.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/ArchivePathElement$DirectoryEntryException.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/ArchivePathElement$1.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/ArchivePathElement$1$1.class"));
        assertTrue(classList.contains("com/cantalou/gradle/dex/multidex/Path.class"));
    }
}