package org.benf.cfr.test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.benf.cfr.test.DecompilationTestImplementation.ClassFileTestDataSource;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * CFR decompilation test. For increased readability this class only contains the JUnit test methods;
 * the actual implementation is in {@link DecompilationTestImplementation}.
 */
class DecompilationTest {
    @ParameterizedTest(name = "[{index}] {0}")
    @ClassFileTestDataSource("classes.xml")
    void classFile(Path classFilePath, Map<String, String> cfrOptionsDict, Path output, String outputPrefix) throws IOException {
        DecompilationTestImplementation.assertClassFile(classFilePath, cfrOptionsDict, output, outputPrefix);
    }
}
