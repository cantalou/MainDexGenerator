package com.cantalou.gradle.dex.tramsform

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.api.transform.*
import com.android.build.gradle.internal.core.VariantConfiguration
import com.android.build.gradle.internal.dsl.DexOptions
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.MultiDexTransform
import com.android.builder.sdk.TargetInfo
import com.android.ide.common.process.ProcessException
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.io.Files
import com.cantalou.gradle.dex.multidex.MainDexListBuilder
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import proguard.ParseException

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.function.Function
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

/**
 *
 * 替换MultiDexTransform, 自定义生成mainDexListFile
 *
 * @author cantalou* @date 2018年09月17日 16:30
 *
 */
@SuppressWarnings("unchecked")
class CustomMainDexTransform extends Transform {

    @NonNull
    @InputFiles
    File manifestKeepListFile

    @Nullable
    File userMainDexKeepFile

    @NonNull
    VariantScope variantScope

    @Nullable
    File includeInMainDexJarFile

    boolean keepRuntimeAnnotatedClasses

    @NonNull
    File mainDexListFile

    @OutputFile
    File outputDir

    CustomMainDexTransform(VariantScope variantScope, DexOptions dexOptions, File includeInMainDexJarFile) {
        this.variantScope = variantScope
        this.includeInMainDexJarFile = includeInMainDexJarFile
        userMainDexKeepFile = variantScope.getVariantConfiguration().getMultiDexKeepFile()
        keepRuntimeAnnotatedClasses = dexOptions.getKeepRuntimeAnnotatedClasses()
    }

    @NonNull
    @Override
    String getName() {
        return "multidexlist"
    }

    @NonNull
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return (Set) ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES)
    }

    @NonNull
    @Override
    Set<QualifiedContent.ContentType> getOutputTypes() {
        return ImmutableSet.of()
    }

    @NonNull
    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES
    }

    @NonNull
    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @NonNull
    @Override
    Collection<SecondaryFile> getSecondaryFiles() {
        Collection result = Arrays.asList(manifestKeepListFile, userMainDexKeepFile, includeInMainDexJarFile)
                .stream()
                .filter(new java.util.function.Predicate<File>()
                {
                    @Override
                    boolean test(File file) {
                        return file != null
                    }
                })
                .map(new Function<File, SecondaryFile>()
                {
                    @Override
                    SecondaryFile apply(File file) {
                        return SecondaryFile.nonIncremental(file)
                    }
                })
                .collect(Collectors.toList())
        return result
    }

    @NonNull
    @Override
    Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> params = ImmutableMap.builder()
        params.put("keepRuntimeAnnotatedClasses", keepRuntimeAnnotatedClasses)
        TargetInfo targetInfo = variantScope.getGlobalScope()
                .getAndroidBuilder()
                .getTargetInfo()
        if (targetInfo != null) {
            params.put("build_tools", targetInfo.getBuildTools()
                    .getRevision()
                    .toString())
        }
        return params.build()
    }

    @NonNull
    @Override
    Collection<File> getSecondaryFileOutputs() {
        return Lists.newArrayList(mainDexListFile)
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(@NonNull TransformInvocation invocation) throws IOException, TransformException, InterruptedException {
        try {
            File input = verifyInputs(invocation.getReferencedInputs())
            shrinkJar(input)
            computeList(input)
        }
        catch (ParseException | ProcessException e) {
            throw new TransformException(e)
        }
    }

    private static File verifyInputs(@NonNull Collection<TransformInput> inputs) {
        // Collect the inputs. There should be only one.
        List<File> inputFiles = Lists.newArrayList()

        for (TransformInput transformInput : inputs) {
            for (JarInput jarInput : transformInput.getJarInputs()) {
                inputFiles.add(jarInput.getFile())
            }

            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                inputFiles.add(directoryInput.getFile())
            }
        }

        return Iterables.getOnlyElement(inputFiles)
    }

    /**
     * Extract class from "input".jar by comparing className which was listed in "manifestKeepListFile" file
     * @param input
     * @throws IOException* @throws ParseException
     */
    private void shrinkJar(@NonNull File input) throws IOException, ParseException {

        List<String> manifestClass = manifestKeepListFile.readLines()
        //for instant-run
        manifestClass << "com/android/tools/fd"

        ZipFile inputZip = null
        ZipOutputStream outputZip = null
        try {
            inputZip = new ZipFile(input)
            outputZip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(variantScope.getProguardComponentsJarFile())))
            Enumeration<? extends ZipEntry> iterator = inputZip.entries()
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement()
                def name = entry.getName()
                for (String namePrefix : manifestClass) {
                    if (name.startsWith(namePrefix)) {
                        ZipEntry newEntry = new ZipEntry(name)
                        outputZip.putNextEntry(newEntry)
                        byte[] buffer = new byte[16 * 1024]
                        InputStream is = inputZip.getInputStream(entry)
                        for (int length = is.read(buffer); length != -1; length = is.read(buffer)) {
                            outputZip.write(buffer, 0, length)
                        }
                        outputZip.closeEntry()
                        break
                    }
                }
            }
        }
        finally {
            inputZip.close()
            outputZip.close()
        }
    }

    private void computeList(File _allClassesJarFile) throws ProcessException, IOException {
        // manifest components plus immediate dependencies must be in the main dex.
        Set<String> mainDexClasses = callDx(_allClassesJarFile, variantScope.getProguardComponentsJarFile())

        if (userMainDexKeepFile != null) {
            mainDexClasses = ImmutableSet.<String> builder().addAll(mainDexClasses)
                    .addAll(Files.readLines(userMainDexKeepFile, Charsets.UTF_8))
                    .build()
        }
        String fileContent = Joiner.on(System.getProperty("line.separator")).join(mainDexClasses)
        Files.write(fileContent, mainDexListFile, Charsets.UTF_8)
    }

    private Set<String> callDx(File allClassesJarFile, File jarOfRoots) throws ProcessException {
        if (!keepRuntimeAnnotatedClasses) {
            Logging.getLogger(MultiDexTransform.class)
                    .warn("Not including classes with runtime retention annotations in the main dex.\n" + "This can cause issues with reflection in older platforms.")
        }

        List<String> args = new ArrayList<>()
        if (!keepRuntimeAnnotatedClasses) {
            args.add("--disable-annotation-resolution-workaround")
        }
        args.add(jarOfRoots.getAbsolutePath())
        args.add(allClassesJarFile.getAbsolutePath())
        MainDexListBuilder.main(args.toArray(new String[0]))
        return MainDexListBuilder.getMainDexList()
    }


}
