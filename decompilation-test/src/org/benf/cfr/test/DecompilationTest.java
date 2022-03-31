package org.benf.cfr.test;

import java.io.IOException;
import java.nio.file.Path;

import org.benf.cfr.test.DecompilationTestImplementation.ClassFileTestDataSource;
import org.benf.cfr.test.DecompilationTestImplementation.JarTestDataSource;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * CFR decompilation test. For increased readability this class only contains the JUnit test methods;
 * the actual implementation is in {@link DecompilationTestImplementation}.
 */
class DecompilationTest {
    @ParameterizedTest(name = "[{index}] {0}")
    @ClassFileTestDataSource("classes")
    void classFile(String displayName, Path classFilePath, Path cfrOptionsFilePath, Path expectedSummaryPath, Path expectedExceptionsPath, Path expectedJavaPath) throws IOException {
        DecompilationTestImplementation.assertClassFile(classFilePath, cfrOptionsFilePath, expectedSummaryPath, expectedExceptionsPath, expectedJavaPath);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @JarTestDataSource("jars")
    void jar(String displayName, Path jarPath, Path cfrOptionsFilePath, Path expectedSummaryPath, Path expectedExceptionsPath, Path expectedJavaFilesDirPath) throws IOException {
        DecompilationTestImplementation.assertJar(jarPath, cfrOptionsFilePath, expectedSummaryPath, expectedExceptionsPath, expectedJavaFilesDirPath);
    }
}
