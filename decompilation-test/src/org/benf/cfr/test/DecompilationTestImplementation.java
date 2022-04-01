package org.benf.cfr.test;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.OutputSinkFactory.Sink;
import org.benf.cfr.reader.api.SinkReturns.DecompiledMultiVer;
import org.benf.cfr.reader.api.SinkReturns.ExceptionMessage;
import org.benf.cfr.reader.util.CfrVersionInfo;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
    private static final Path TEST_SPECS_DIR;
    private static final Path TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR;
    static {
        Path decompilationTestDir = Paths.get("decompilation-test");
        TEST_DATA_ROOT_DIR = decompilationTestDir.resolve("test-data");
        TEST_SPECS_DIR = decompilationTestDir.resolve("test-specs");
        TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR = decompilationTestDir.resolve("test-data-expected-output");
    }

    /**
     * Directory where diff files for test failures should be written. Uses the Maven
     * {@code target} directory.
     */
    private static final Path TEST_FAILURE_DIFF_OUTPUT_DIR = Paths.get("target", "cfr-test-failures-diff");

    private static final String EXPECTED_SUMMARY_FILE_EXTENSION = ".expected.summary";
    private static final String EXPECTED_EXCEPTIONS_FILE_EXTENSION = ".expected.exceptions";
    private static final String EXPECTED_SOURCE_CODE_FILE_EXTENSION = ".expected.java";

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

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ArgumentsSource(ClassFileTestDataProvider.class)
    @interface ClassFileTestDataSource {
        /** Name of the file containing the class files test specifications */
        String value();
    }

    static class ClassFileTestDataProvider implements ArgumentsProvider, AnnotationConsumer<ClassFileTestDataSource> {
        private String configFilePath;
        // Yes, using terrible built in xml support.
        private DocumentBuilderFactory dbf;

        @Override
        public void accept(ClassFileTestDataSource annotation) {
            configFilePath = annotation.value();
            dbf = DocumentBuilderFactory.newInstance();
        }

        public static void foo() {
            throw new IllegalStateException();
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {

            List<Arguments> res = new ArrayList<>();

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(TEST_SPECS_DIR.resolve(configFilePath).toString());

            /* discover tests we want to do from config file; this is expected to match the
             * binary files in the relevant directory.
             */
            doc.getDocumentElement().normalize();
            NodeList classes = doc.getElementsByTagName("class");

            for (int x=0;x<classes.getLength();++x) {
                Element clazz = (Element)classes.item(x);

                // Should be using path selector here, content is simple enough to cheat.
                String classFilePath = clazz.getElementsByTagName("path").item(0).getTextContent();
                String name = clazz.getElementsByTagName("name").item(0).getTextContent();
                String label = clazz.getElementsByTagName("label").item(0).getTextContent();
                Map<String, String> options = new HashMap<>();
                NodeList optionNodes = clazz.getElementsByTagName("option");
                for (int o=0;o<optionNodes.getLength();++o) {
                    NamedNodeMap attrs = optionNodes.item(o).getAttributes();
                    String key = attrs.getNamedItem("key").getTextContent();
                    String value = attrs.getNamedItem("value").getTextContent();
                    options.put(key, value);
                }
                Path expectedSource = getTestDataSubDir(classFilePath).resolve(name + ".class");
                Path expectedTgt = TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR.resolve("classes");

                res.add(Arguments.of(expectedSource, options, expectedTgt, name + "." + label));
            }
            return res.stream();
        }
    }

    private static Map<String, String> createOptionsMap(Map<String, String> baseOptions) {
        Map<String, String> options = new HashMap<>();

        // Do not include CFR version, would otherwise cause source changes when switching CFR version
        options.put(OptionsImpl.SHOW_CFR_VERSION.getName(), "false");
        // Don't dump exception stack traces because they might differ depending on how these tests are started (different IDEs, Maven, ...)
        options.put(OptionsImpl.DUMP_EXCEPTION_STACK_TRACE.getName(), "false");

        for (Map.Entry<String, String> kvp : baseOptions.entrySet()) {
            options.put(kvp.getKey(), kvp.getValue());
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

    static void assertClassFile(Path classFilePath, Map<String, String> baseOptions, Path outputDir, String filePrefix) throws IOException {
        Map<String, String> options = createOptionsMap(baseOptions);
        DecompilationResult decompilationResult = decompile(classFilePath, options);

        boolean createdExpectedFile = false;
        boolean updatedExpectedFile = false;
        List<Path> notUpdatableDueToDecompilationNotes = new ArrayList<>();

        List<DecompiledMultiVer> decompiledList = decompilationResult.decompiled;
        assertEquals(1, decompiledList.size());
        DecompiledMultiVer decompiled = decompiledList.get(0);
        assertEquals(0, decompiled.getRuntimeFrom());
        String actualJavaCode = decompiled.getJava();

        Path expectedJavaPath = outputDir.resolve(filePrefix + EXPECTED_SOURCE_CODE_FILE_EXTENSION);
        Path expectedSummaryPath = outputDir.resolve(filePrefix + EXPECTED_SUMMARY_FILE_EXTENSION);
        Path expectedExceptionsPath = outputDir.resolve(filePrefix + EXPECTED_EXCEPTIONS_FILE_EXTENSION);

        if (!Files.exists(expectedJavaPath)) {
            if (CREATE_EXPECTED_DATA_IF_MISSING) {
                createdExpectedFile = true;
                writeString(expectedJavaPath, actualJavaCode);
            } else {
                throwTestSetupError("Missing file: " + expectedJavaPath + " (Create with -Dcfr.decompilation-test.create-expected)");
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
}
