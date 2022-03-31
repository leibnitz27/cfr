package org.benf.cfr.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.OutputSinkFactory.Sink;
import org.benf.cfr.reader.api.SinkReturns.DecompiledMultiVer;
import org.benf.cfr.reader.api.SinkReturns.ExceptionMessage;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.CfrVersionInfo;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

/**
 * Implementation for {@link DecompilationTest}.
 */
class DecompilationTestImplementation {
    /**
     * Create files representing expected data, in case they do not exist yet.
     * This is intended to simplify adding new test data. If due to this new files
     * are generated, the corresponding test is marked as failed, to avoid using
     * this setting during regular test execution by accident, which would otherwise
     * erroneously make the tests succeed.
     */
    private static final boolean CREATE_EXPECTED_DATA_IF_MISSING = System.getProperty("cfr.decompilation-test.create-expected") != null;

    /**
     * Updates the expected data, in case it does not match the actual data.
     * This is intended to simplify adjusting the expected output in case a
     * decompiler change affects multiple tests. If due to this the expected
     * data is updated, the corresponding test is marked as failed, to avoid using
     * this setting during regular test execution by accident, which would otherwise
     * erroneously make the tests succeed.
     */
    private static final boolean UPDATE_EXPECTED_DATA = System.getProperty("cfr.decompilation-test.update-expected") != null;

    /**
     * Path of the directory containing all test data. Directory might not exist or might be
     * empty in case the Git submodule was not cloned. In this case the test should fail.
     * This field should not be accessed directly; instead {@link #getTestDataSubDir(String)}
     * should be used.
     *
     * <p><b>Important:</b> This is a separate directory and not part of the test class path
     * to avoid that the test data class files are loaded by accident and interfere with the
     * test execution.
     */
    private static final Path TEST_DATA_ROOT_DIR;
    private static final Path TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR;
    static {
        Path decompilationTestDir = Paths.get("decompilation-test");
        TEST_DATA_ROOT_DIR = decompilationTestDir.resolve("test-data");
        TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR = decompilationTestDir.resolve("test-data-expected-output");
    }

    /**
     * Directory where diff files for test failures should be written. Uses the Maven
     * {@code target} directory.
     */
    private static final Path TEST_FAILURE_DIFF_OUTPUT_DIR = Paths.get("target", "cfr-test-failures-diff");

    private static final String OPTIONS_FILE_EXTENSION = ".options";
    private static final String EXPECTED_SUMMARY_FILE_EXTENSION = ".expected.summary";
    private static final String EXPECTED_EXCEPTIONS_FILE_EXTENSION = ".expected.exceptions";
    private static final String EXPECTED_SOURCE_CODE_FILE_EXTENSION = ".expected.java";

    private static final String JAR_OPTIONS_FILE_NAME = "_" + OPTIONS_FILE_EXTENSION;
    private static final String JAR_EXPECTED_SUMMARY_FILE_NAME = "_" + EXPECTED_SUMMARY_FILE_EXTENSION;
    private static final String JAR_EXPECTED_EXCEPTIONS_FILE_NAME = "_" + EXPECTED_EXCEPTIONS_FILE_EXTENSION;

    private static final Set<String> JAR_SPECIAL_EXPECTED_FILE_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        JAR_OPTIONS_FILE_NAME,
        JAR_EXPECTED_SUMMARY_FILE_NAME,
        JAR_EXPECTED_EXCEPTIONS_FILE_NAME
    )));

    private static Path getTestDataSubDir(String subDirPath) throws Exception {
        Path path = TEST_DATA_ROOT_DIR.resolve(subDirPath);
        if (!Files.isDirectory(path)) {
            throw new ExtensionConfigurationException("Directory '" + path + "' does not exist; make sure "
                + "that the test data Git submodule has been cloned properly.");
        }

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
            if (!dirStream.iterator().hasNext()) {
                throw new ExtensionConfigurationException("Directory '" + path + "' is empty; make sure "
                    + "that the test data Git submodule has been cloned properly.");
            }
        }

        return path;
    }

    private static class IgnoredFilesMatcher {
        private final Path enclosingDir;
        private final List<PathMatcher> matchers;

        private IgnoredFilesMatcher(Path enclosingDir, List<PathMatcher> matchers) {
            this.enclosingDir = enclosingDir;
            this.matchers = matchers;
        }

        private static String escapeForPathMatcher(String s) {
         // Must escape backslashes for FileSystem.getPathMatcher()
            return s.replace("\\", "\\\\");
        }

        public static IgnoredFilesMatcher fromPaths(Path enclosingDir, String... paths) {
            FileSystem fileSystem = FileSystems.getDefault();
            String escapedSeparator = escapeForPathMatcher(fileSystem.getSeparator());

            List<PathMatcher> matchers = Arrays.stream(paths)
                .map(path -> path.replace("/", escapedSeparator))
                .map(path -> fileSystem.getPathMatcher("glob:" + path))
                .collect(Collectors.toList());
            return new IgnoredFilesMatcher(enclosingDir, matchers);
        }

        public boolean isIgnored(Path path) {
            if (!path.startsWith(enclosingDir)) {
                throw new IllegalArgumentException("Path outside of enclosing directory: " + path);
            }
            Path relativePath = enclosingDir.relativize(path);
            return matchers.stream().anyMatch(matcher -> matcher.matches(relativePath));
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ArgumentsSource(ClassFileTestDataProvider.class)
    @interface ClassFileTestDataSource {
        /** Name of the directory containing the class files */
        String value();

        /**
         * Optional list of {@linkplain FileSystem#getPathMatcher(String) glob patterns} (except only using {@code /}
         * as separator) relative to the {@linkplain #value() specified directory} matching files which should be
         * ignored.
         */
        String[] ignoredFiles() default {};
    }

    private static Path resolveRelativized(Path base, Path toRelativize, Path newBase, String fileNameExtension) {
        Path parentDir = newBase.resolve(base.relativize(toRelativize.getParent()));
        String fileName = toRelativize.getFileName().toString();

        String fileNameWithoutExtension;
        int extensionIndex = fileName.indexOf('.');
        if (extensionIndex != -1) {
            fileNameWithoutExtension = fileName.substring(0, extensionIndex);
        } else {
            fileNameWithoutExtension = fileName;
        }
        return parentDir.resolve(fileNameWithoutExtension + fileNameExtension);
    }

    static class ClassFileTestDataProvider implements ArgumentsProvider, AnnotationConsumer<ClassFileTestDataSource> {
        private String subDirPath;
        private String[] ignoredFilePatterns;

        @Override
        public void accept(ClassFileTestDataSource annotation) {
            System.err.println("HERE");
            subDirPath = annotation.value();
            ignoredFilePatterns = annotation.ignoredFiles();
        }

        public static void foo() {
            throw new IllegalStateException();
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            Path directory = getTestDataSubDir(subDirPath);
            foo();
            System.err.println("Checking for class files @ " + subDirPath);
            IgnoredFilesMatcher ignoredFilesMatcher = IgnoredFilesMatcher.fromPaths(directory, ignoredFilePatterns);

            List<Path> classFilePaths = new ArrayList<>();

            try (Stream<Path> files = Files.walk(directory).filter(Files::isRegularFile).filter(path -> !ignoredFilesMatcher.isIgnored(path))) {
                for (Path path : (Iterable<Path>) files::iterator) {
                    String fileName = path.getFileName().toString();

                    // Ignore class files for nested classes; CFR will load them when the enclosing class is decompiled
                    if (fileName.contains("$")) {
                        String expectedEnclosingClassFileName = fileName.substring(0, fileName.indexOf('$')) + ".class";
                        Path expectedEnclosingClassFilePath = path.resolveSibling(expectedEnclosingClassFileName);

                        if (!Files.exists(expectedEnclosingClassFilePath)) {
                            throw new ExtensionConfigurationException("Enclosing class file '" + expectedEnclosingClassFilePath + "' for '" + fileName + "' is missing");
                        }
                    } else {
                        classFilePaths.add(path);
                    }
                }
            }

            if (classFilePaths.isEmpty()) {
                throw new ExtensionConfigurationException("Directory '" + directory + "' does not contain any class files");
            }

            Path expectedDir = TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR.resolve(subDirPath);
            // Verify that all files in the expected output dir are used
            try (Stream<Path> files = Files.walk(expectedDir).filter(Files::isRegularFile)) {
                for (Path path : (Iterable<Path>) files::iterator) {
                    Path parentDir = directory.resolve(expectedDir.relativize(path.getParent()));
                    String fileName = path.getFileName().toString();

                    int extensionIndex = fileName.indexOf('.');
                    if (extensionIndex == -1) {
                        throw new IllegalArgumentException("Missing extension for " + path);
                    }
                    Path testDataPath = parentDir.resolve(fileName.substring(0, extensionIndex) + ".class");

                    if (!classFilePaths.contains(testDataPath)) {
                        throw new ExtensionConfigurationException("'" + path + "' has no corresponding test data file");
                    }
                }
            }

            return classFilePaths.stream().sorted().map(classFilePath -> {
                String displayName = directory.relativize(classFilePath).toString();
                Path cfrOptionsFilePath = resolveRelativized(directory, classFilePath, expectedDir, OPTIONS_FILE_EXTENSION);
                Path expectedSummaryPath = resolveRelativized(directory, classFilePath, expectedDir, EXPECTED_SUMMARY_FILE_EXTENSION);
                Path expectedExceptionsPath = resolveRelativized(directory, classFilePath, expectedDir, EXPECTED_EXCEPTIONS_FILE_EXTENSION);
                Path expectedJavaPath = resolveRelativized(directory, classFilePath, expectedDir, EXPECTED_SOURCE_CODE_FILE_EXTENSION);

                return Arguments.of(displayName, classFilePath, cfrOptionsFilePath, expectedSummaryPath, expectedExceptionsPath, expectedJavaPath);
            });
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ArgumentsSource(JarTestDataProvider.class)
    @interface JarTestDataSource {
        /** Name of the directory containing the JAR files */
        String value();

        /**
         * Optional list of {@linkplain FileSystem#getPathMatcher(String) glob patterns} (except only using {@code /}
         * as separator) relative to the {@linkplain #value() specified directory} matching files which should be
         * ignored.
         */
        String[] ignoredFiles() default {};
    }

    static class JarTestDataProvider implements ArgumentsProvider, AnnotationConsumer<JarTestDataSource> {
        private String subDirPath;
        private String[] ignoredFilePatterns;

        @Override
        public void accept(JarTestDataSource annotation) {
            subDirPath = annotation.value();
            ignoredFilePatterns = annotation.ignoredFiles();
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            Path directory = getTestDataSubDir(subDirPath);
            IgnoredFilesMatcher ignoredFilesMatcher = IgnoredFilesMatcher.fromPaths(directory, ignoredFilePatterns);

            List<Path> jarFilePaths;
            try (Stream<Path> files = Files.walk(directory).filter(Files::isRegularFile).filter(path -> !ignoredFilesMatcher.isIgnored(path))) {
                jarFilePaths = files.collect(Collectors.toList());
            }

            if (jarFilePaths.isEmpty()) {
                throw new ExtensionConfigurationException("Directory '" + directory + "' does not contain any JAR files");
            }

            Path expectedDir = TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR.resolve(subDirPath);
            List<Path> expectedDataDirs = jarFilePaths.stream()
                .map(path -> resolveRelativized(directory, path, expectedDir, ""))
                .collect(Collectors.toList());

            // Verify that all expected data directories are used; otherwise they would be silently ignored
            // when corresponding test data does not exist anymore
            Files.walkFileTree(expectedDir, new SimpleFileVisitor<Path>() {
                boolean hasSubDirs = false;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    hasSubDirs = true;
                    if (expectedDataDirs.contains(dir)) {
                        // Can skip this subtree because it is used
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Mark as false and check whether subtree set it to true when reaching postVisitDirectory
                    hasSubDirs = false;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }

                    // Check if leaf directory was found and it does not have a data directory parent
                    // (otherwise this directory would have been skipped by that parent)
                    if (!hasSubDirs) {
                        throw new ExtensionConfigurationException("Directory without corresponding JAR file: " + dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            return jarFilePaths.stream().sorted().map(jarPath -> {
                String displayName = directory.relativize(jarPath).toString();
                Path expectedDataDir = resolveRelativized(directory, jarPath, expectedDir, "");

                // Place these files in the same directory to have IDE and OS group them properly;
                // placing them outside of directory might cause them to be displayed separately
                // because some IDEs and OS' group directories and files separately
                Path cfrOptionsFilePath = expectedDataDir.resolve(JAR_OPTIONS_FILE_NAME);
                Path expectedSummaryPath = expectedDataDir.resolve(JAR_EXPECTED_SUMMARY_FILE_NAME);
                Path expectedExceptionsPath = expectedDataDir.resolve(JAR_EXPECTED_EXCEPTIONS_FILE_NAME);

                return Arguments.of(displayName, jarPath, cfrOptionsFilePath, expectedSummaryPath, expectedExceptionsPath, expectedDataDir);
            });
        }
    }

    private static Map<String, String> createOptionsMap(Path cfrOptionsFilePath) throws IOException {
        Map<String, String> options = new HashMap<>();
        // Do not include CFR version, would otherwise cause source changes when switching CFR version
        options.put(OptionsImpl.SHOW_CFR_VERSION.getName(), "false");
        // Don't dump exception stack traces because they might differ depending on how these tests are started (different IDEs, Maven, ...)
        options.put(OptionsImpl.DUMP_EXCEPTION_STACK_TRACE.getName(), "false");

        if (Files.exists(cfrOptionsFilePath)) {
            for (String line : Files.readAllLines(cfrOptionsFilePath)) {
                // Ignore comments and empty lines
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] option = line.split(" ", 2);
                options.put(option[0], option[1]);
            }
        }
        return options;
    }

    private static String readNormalizedString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replaceAll("\\R", "\n");
    }

    private static class DecompilationResult {
        public final String summary;
        public final String exceptions;
        public final List<DecompiledMultiVer> decompiled;

        public DecompilationResult(String summary, String exceptions, List<DecompiledMultiVer> decompiled) {
            this.summary = summary;
            this.exceptions = exceptions;
            this.decompiled = decompiled;
        }
    }

    private static DecompilationResult decompile(Path path, Map<String, String> options) {
        StringWriter summaryOutput = new StringWriter();
        Sink<String> summarySink = summaryOutput::append; // Messages include line terminator, therefore only print

        StringWriter exceptionsOutput = new StringWriter();
        Sink<ExceptionMessage> exceptionSink = exceptionMessage -> {
            exceptionsOutput.append(exceptionMessage.getPath()).append('\n');
            exceptionsOutput.append(exceptionMessage.getMessage()).append('\n');

            Exception exception = exceptionMessage.getThrownException();
            exceptionsOutput
                .append(exception.getClass().getName())
                .append(": ")
                .append(exception.getMessage())
                .append("\n\n");
        };

        List<DecompiledMultiVer> decompiledList = new ArrayList<>();
        Sink<DecompiledMultiVer> decompiledSourceSink = decompiledList::add;

        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                switch (sinkType) {
                    case JAVA:
                        return Collections.singletonList(SinkClass.DECOMPILED_MULTIVER);
                    case EXCEPTION:
                        return Collections.singletonList(SinkClass.EXCEPTION_MESSAGE);
                    case SUMMARY:
                        return Collections.singletonList(SinkClass.STRING);
                    default:
                        // Required to always support STRING
                        return Collections.singletonList(SinkClass.STRING);
                }
            }

            @SuppressWarnings("unchecked")
            private <T> Sink<T> castSink(Sink<?> sink) {
                return (Sink<T>) sink;
            }

            @Override
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                switch (sinkType) {
                case JAVA:
                    if (sinkClass != SinkClass.DECOMPILED_MULTIVER) {
                        throw new IllegalArgumentException("Sink class " + sinkClass + " is not supported for decompiled output");
                    }
                    return castSink(decompiledSourceSink);
                case EXCEPTION:
                    switch (sinkClass) {
                        case EXCEPTION_MESSAGE:
                            return castSink(exceptionSink);
                        // Always have to support STRING
                        case STRING:
                            return castSink(summarySink);
                        default:
                            throw new IllegalArgumentException("Sink factory does not support " + sinkClass);
                    }
                case SUMMARY:
                    return castSink(summarySink);
                default:
                    return ignored -> { };
                }
            }
        };

        CfrDriver driver = new CfrDriver.Builder()
            .withOptions(options)
            .withOutputSink(sinkFactory)
            .build();
        String pathString = path.toAbsolutePath().toString();
        driver.analyse(Collections.singletonList(pathString));

        // Replace version information and file path to prevent changes in the output
        String summary = summaryOutput.toString().replace(CfrVersionInfo.VERSION_INFO, "<version>").replace(pathString, "<path>/" + path.getFileName().toString());
        return new DecompilationResult(summary, exceptionsOutput.toString(), decompiledList);
    }

    private static void writeString(Path path, String s) throws IOException {
        Files.write(path, s.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripDecompilationNotes(String expectedCode) {
        String[] lines = expectedCode.split("\\R", -1);
        StringJoiner strippedCodeJoiner = new StringJoiner("\n");

        for (String line : lines) {
            // Ignore if line is a decompilation note, starting with: //#
            if (!line.trim().startsWith("//#")) {
                // Remove inline decompilation notes: /*# ... #*/
                strippedCodeJoiner.add(line.replaceAll("/\\*#.+#\\*/", ""));
            }
        }

        return strippedCodeJoiner.toString();
    }

    private static class DiffCodeResult {
        /**
         * Assertion error in case the expected test data differed from the actual test data.
         * {@code null} if they were the same, or if {@link #updatedExpectedData} or
         * {@link #decompilationNotesPreventedUpdate} is {@code true}.
         */
        public final AssertionError assertionError;
        /**
         * Indicates whether due to {@link DecompilationTestImplementation#UPDATE_EXPECTED_DATA}
         * the expected test data was updated because it differed from the actual test data.
         */
        public final boolean updatedExpectedData;
        /**
         * Whether updating the expected test data was not possible because it contains decompilation
         * notes which would get lost.
         */
        public final boolean decompilationNotesPreventedUpdate;

        public DiffCodeResult(AssertionError assertionError, boolean updatedExpectedData, boolean decompilationNotesPreventedUpdate) {
            this.assertionError = assertionError;
            this.updatedExpectedData = updatedExpectedData;
            this.decompilationNotesPreventedUpdate = decompilationNotesPreventedUpdate;
        }
    }

    /**
     * Compares the content of the specified file with the {@code actualCode} and returns a {@link DiffCodeResult}.
     * If there is a difference between the expected and the actual data, a diff file is created in
     * {@link #TEST_FAILURE_DIFF_OUTPUT_DIR} (or a subdirectory).
     */
    private static DiffCodeResult diffCodeAndWriteOnMismatch(Path expectedCodeFilePath, String actualCode) throws IOException {
        String originalExpectedCode = readNormalizedString(expectedCodeFilePath);
        String expectedCodeWithoutNotes = stripDecompilationNotes(originalExpectedCode);

        AssertionError assertionError;
        try {
            // Trigger AssertionError and later throw that because IDEs often support diff
            // functionality for the values
            assertEquals(expectedCodeWithoutNotes, actualCode);
            return new DiffCodeResult(null, false, false);
        } catch (AssertionError e) {
            assertionError = e;
        }

        if (UPDATE_EXPECTED_DATA) {
            // If no decompilation notes are contained, can update the file; otherwise the decompilation notes
            // would be lost
            if (originalExpectedCode.equals(expectedCodeWithoutNotes)) {
                writeString(expectedCodeFilePath, actualCode);
                return new DiffCodeResult(null, true, false);
            } else {
                return new DiffCodeResult(null, false, true);
            }
        } else {
            List<String> expectedLines = Arrays.asList(expectedCodeWithoutNotes.split("\\R", -1));
            List<String> actualLines = Arrays.asList(actualCode.split("\\R", -1));
            Patch<String> diff = DiffUtils.diff(expectedLines, actualLines);

            String fileName = expectedCodeFilePath.getFileName().toString();
            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(fileName, "actual-code", expectedLines, diff, 1);
            Path outputPath = TEST_FAILURE_DIFF_OUTPUT_DIR.resolve(TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR.toAbsolutePath().relativize(expectedCodeFilePath.toAbsolutePath()).getParent().resolve(fileName + ".diff"));
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, unifiedDiff);

            return new DiffCodeResult(assertionError, false, false);
        }
    }

    private static void throwTestSetupError(String message) {
        fail("Test setup error: " + message);
    }

    /**
     * Fail the test because missing expected data was created.
     *
     * @see #CREATE_EXPECTED_DATA_IF_MISSING
     */
    private static void failCreatedMissingExpectedData() {
        fail("Created missing expected data");
    }

    /**
     * Fail the test because expected data was updated.
     *
     * @param filesWithDecompilationNotes
     *      List of expected Java files which could not be updated because they contain
     *      decompilation notes
     * @see #UPDATE_EXPECTED_DATA
     */
    private static void failUpdatedExpectedData(List<Path> filesWithDecompilationNotes) {
        String message = "Updated expected data";

        if (!filesWithDecompilationNotes.isEmpty()) {
            String filesList = filesWithDecompilationNotes.stream().map(Path::toString).collect(Collectors.joining(", "));
            message += "; but failed updating these files due to decompilation notes which would get lost: " + filesList;
        }
        fail(message);
    }

    /**
     * Fail the test because expected data could not be updated because it contains
     * decompilation notes which would get lost.
     *
     * @param filesWithDecompilationNotes
     *      List of expected Java files which could not be updated because they contain
     *      decompilation notes
     * @see #UPDATE_EXPECTED_DATA
     */
    private static void failNotUpdatableDueToDecompilationNotes(List<Path> filesWithDecompilationNotes) {
        String filesList = filesWithDecompilationNotes.stream().map(Path::toString).collect(Collectors.joining(", "));
        fail("Failed updating these files due to decompilation notes which would get lost: " + filesList);
    }

    /**
     * Asserts that the content of the file is equal to the {@code actualData}. Returns {@code true} if
     * they are equal. Otherwise, if {@link #UPDATE_EXPECTED_DATA} is {@code true} the expected data file
     * is updated and {@code false} is returned; otherwise the corresponding {@link AssertionError} is
     * thrown.
     */
    private static boolean assertFileEquals(Path expectedDataFile, String actualData) throws IOException {
        try {
            assertEquals(readNormalizedString(expectedDataFile), actualData);
            return true;
        } catch (AssertionError assertionError) {
            if (UPDATE_EXPECTED_DATA) {
                writeString(expectedDataFile, actualData);
                return false;
            } else {
                throw assertionError;
            }
        }
    }

    static void assertClassFile(Path classFilePath, Path cfrOptionsFilePath, Path expectedSummaryPath, Path expectedExceptionsPath, Path expectedJavaPath) throws IOException {
        Map<String, String> options = createOptionsMap(cfrOptionsFilePath);
        DecompilationResult decompilationResult = decompile(classFilePath, options);

        boolean createdExpectedFile = false;
        boolean updatedExpectedFile = false;
        List<Path> notUpdatableDueToDecompilationNotes = new ArrayList<>();

        List<DecompiledMultiVer> decompiledList = decompilationResult.decompiled;
        assertEquals(1, decompiledList.size());
        DecompiledMultiVer decompiled = decompiledList.get(0);
        assertEquals(0, decompiled.getRuntimeFrom());
        String actualJavaCode = decompiled.getJava();

        if (!Files.exists(expectedJavaPath)) {
            if (CREATE_EXPECTED_DATA_IF_MISSING) {
                createdExpectedFile = true;
                writeString(expectedJavaPath, actualJavaCode);
            } else {
                throwTestSetupError("Missing file: " + expectedJavaPath);
            }
        } else {
            DiffCodeResult diffCodeResult = diffCodeAndWriteOnMismatch(expectedJavaPath, actualJavaCode);
            AssertionError assertionError = diffCodeResult.assertionError;
            if (assertionError != null) {
                throw assertionError;
            }

            updatedExpectedFile |= diffCodeResult.updatedExpectedData;
            if (diffCodeResult.decompilationNotesPreventedUpdate) {
                notUpdatableDueToDecompilationNotes.add(expectedJavaPath);
            }
        }

        String actualSummary = decompilationResult.summary;
        if (!actualSummary.isEmpty() || Files.exists(expectedSummaryPath)) {
            if (!Files.exists(expectedSummaryPath)) {
                if (CREATE_EXPECTED_DATA_IF_MISSING) {
                    createdExpectedFile = true;
                    writeString(expectedSummaryPath, actualSummary);
                } else {
                    throwTestSetupError("Missing file: " + expectedSummaryPath);
                }
            } else {
                if (!assertFileEquals(expectedSummaryPath, actualSummary)) {
                    updatedExpectedFile = true;
                }
            }
        }

        String actualExceptions = decompilationResult.exceptions;
        if (!actualExceptions.isEmpty() || Files.exists(expectedExceptionsPath)) {
            if (!Files.exists(expectedExceptionsPath)) {
                if (CREATE_EXPECTED_DATA_IF_MISSING) {
                    createdExpectedFile = true;
                    writeString(expectedExceptionsPath, actualExceptions);
                } else {
                    throwTestSetupError("Missing file: " + expectedExceptionsPath);
                }
            } else {
                if (!assertFileEquals(expectedExceptionsPath, actualExceptions)) {
                    updatedExpectedFile = true;
                }
            }
        }

        if (createdExpectedFile) {
            failCreatedMissingExpectedData();
        }
        if (updatedExpectedFile) {
            failUpdatedExpectedData(notUpdatableDueToDecompilationNotes);
        }
        if (!notUpdatableDueToDecompilationNotes.isEmpty()) {
            failNotUpdatableDueToDecompilationNotes(notUpdatableDueToDecompilationNotes);
        }
    }

    private static Path resolveSafely(Path parent, String child) {
        if (parent.getNameCount() == 0) {
            throw new IllegalArgumentException("Parent path must consist of at least one name");
        }

        Path childPath = parent.getFileSystem().getPath(child);

        if (childPath.isAbsolute()) {
            throw new IllegalArgumentException("Child must not be absolute");
        }
        if (childPath.getNameCount() == 0) {
            throw new IllegalArgumentException("Child must not be empty");
        }

        Path parentNormalized = parent.normalize();
        Path resolvedNormalized = parentNormalized.resolve(childPath).normalize();

        if (!resolvedNormalized.startsWith(parentNormalized)
            || !resolvedNormalized.endsWith(childPath)
        ) {
            throw new IllegalArgumentException("Malformed child: " + childPath);
        }
        else {
            return parent.resolve(childPath);
        }
    }

    private static Path getExpectedPathForDecompiled(Path parent, DecompiledMultiVer decompiled) {
        // Note: Does not use `.expected.java` to avoid confusion with class or package name
        String fileName = decompiled.getPackageName() + '.' + decompiled.getClassName() + ".java";
        String subPath;

        int javaVersion = decompiled.getRuntimeFrom();
        if (javaVersion != 0) {
            subPath = "java-" + javaVersion + "/" + fileName;
        } else {
            subPath = fileName;
        }

        // Resolve safely to avoid writing file outside of intended directory
        return resolveSafely(parent, subPath);
    }

    static void assertJar(Path jarPath, Path cfrOptionsFilePath, Path expectedSummaryPath, Path expectedExceptionsPath, Path expectedJavaFilesDirPath) throws IOException {
        Map<String, String> options = createOptionsMap(cfrOptionsFilePath);
        options.put(OptionsImpl.ANALYSE_AS.getName(), AnalysisType.JAR.name());

        DecompilationResult decompilationResult = decompile(jarPath, options);
        List<DecompiledMultiVer> decompiledList = decompilationResult.decompiled;

        boolean createdExpectedFile = false;
        boolean updatedExpectedFile = false;
        List<Path> notUpdatableDueToDecompilationNotes = new ArrayList<>();

        if (!Files.exists(expectedJavaFilesDirPath)) {
            if (CREATE_EXPECTED_DATA_IF_MISSING) {
                createdExpectedFile = true;

                for (DecompiledMultiVer decompiled : decompiledList) {
                    Path filePath = getExpectedPathForDecompiled(expectedJavaFilesDirPath, decompiled);
                    // Create parent directory for every file to account for files which are nested
                    // inside additional directories
                    Files.createDirectories(filePath.getParent());
                    writeString(filePath, decompiled.getJava());
                }
            } else {
                throwTestSetupError("Missing directory: " + expectedJavaFilesDirPath);
            }
        } else {
            Set<Path> checkedFiles = new HashSet<>();
            // Store error to first process all files and afterwards cause test failure
            AssertionError assertionError = null;

            for (DecompiledMultiVer decompiled : decompiledList) {
                Path filePath = getExpectedPathForDecompiled(expectedJavaFilesDirPath, decompiled);
                if (!Files.exists(filePath)) {
                    throwTestSetupError("Missing file: " + filePath);
                }

                checkedFiles.add(filePath.toAbsolutePath().normalize());

                DiffCodeResult diffCodeResult = diffCodeAndWriteOnMismatch(filePath, decompiled.getJava());
                AssertionError error = diffCodeResult.assertionError;
                if (error != null) {
                    if (assertionError == null) {
                        assertionError = error;
                    } else {
                        assertionError.addSuppressed(error);
                    }
                }

                updatedExpectedFile |= diffCodeResult.updatedExpectedData;
                if (diffCodeResult.decompilationNotesPreventedUpdate) {
                    notUpdatableDueToDecompilationNotes.add(filePath);
                }
            }

            if (assertionError != null) {
                throw assertionError;
            }

            // Verify that CFR produced all expected files
            try (Stream<Path> allFiles = Files.walk(expectedJavaFilesDirPath).filter(Files::isRegularFile)) {
                allFiles.map(path -> path.toAbsolutePath().normalize()).forEach(path -> {
                    String fileName = path.getFileName().toString();

                    if (!JAR_SPECIAL_EXPECTED_FILE_NAMES.contains(fileName) && !checkedFiles.contains(path)) {
                        throwTestSetupError("Expected file was not checked: " + path);
                    }
                });
            }
        }

        String actualSummary = decompilationResult.summary;
        if (!actualSummary.isEmpty() || Files.exists(expectedSummaryPath)) {
            if (!Files.exists(expectedSummaryPath)) {
                if (CREATE_EXPECTED_DATA_IF_MISSING) {
                    createdExpectedFile = true;
                    writeString(expectedSummaryPath, actualSummary);
                } else {
                    throwTestSetupError("Missing file: " + expectedSummaryPath);
                }
            } else {
                if (!assertFileEquals(expectedSummaryPath, actualSummary)) {
                    updatedExpectedFile = true;
                }
            }
        }

        String actualExceptions = decompilationResult.exceptions;
        if (!actualExceptions.isEmpty() || Files.exists(expectedExceptionsPath)) {
            if (!Files.exists(expectedExceptionsPath)) {
                if (CREATE_EXPECTED_DATA_IF_MISSING) {
                    createdExpectedFile = true;
                    writeString(expectedExceptionsPath, actualExceptions);
                } else {
                    throwTestSetupError("Missing file: " + expectedExceptionsPath);
                }
            } else {
                if (!assertFileEquals(expectedExceptionsPath, actualExceptions)) {
                    updatedExpectedFile = true;
                }
            }
        }

        if (createdExpectedFile) {
            failCreatedMissingExpectedData();
        }
        if (updatedExpectedFile) {
            failUpdatedExpectedData(notUpdatableDueToDecompilationNotes);
        }
        if (!notUpdatableDueToDecompilationNotes.isEmpty()) {
            failNotUpdatableDueToDecompilationNotes(notUpdatableDueToDecompilationNotes);
        }
    }
}
