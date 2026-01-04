package org.benf.cfr.test;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns.LineNumberMapping;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineNumberInnerClassTest {
    @Test
    void writesInnerClassLineNumbers() {
        Path classFilePath = Paths.get("decompilation-test", "test-data", "precompiled_tests", "java_8", "org", "benf", "cfr", "tests", "LineNumbersInnerClasses.class");
        assertTrue(Files.exists(classFilePath), "Missing class file: " + classFilePath);

        StringBuilder sb = new StringBuilder();
        NavigableMap<Integer, Integer> lineMapping = new TreeMap<>();

        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                if (sinkType == SinkType.LINENUMBER) {
                    return Arrays.asList(SinkClass.LINE_NUMBER_MAPPING);
                }
                return Arrays.asList(SinkClass.STRING);
            }

            @Override
            public <T> Sink<T> getSink(final SinkType sinkType, final SinkClass sinkClass) {
                return sinkable -> {
                    if (sinkType == SinkType.PROGRESS) {
                        return;
                    }
                    if (sinkType == SinkType.LINENUMBER) {
                        LineNumberMapping mapping = (LineNumberMapping) sinkable;
                        NavigableMap<Integer, Integer> classFileMappings = mapping.getClassFileMappings();
                        NavigableMap<Integer, Integer> mappings = mapping.getMappings();
                        if (classFileMappings != null && mappings != null) {
                            for (Map.Entry<Integer, Integer> entry : mappings.entrySet()) {
                                Integer srcLineNumber = classFileMappings.get(entry.getKey());
                                lineMapping.put(entry.getValue(), srcLineNumber);
                            }
                        }
                        return;
                    }
                    if (sinkType == SinkType.JAVA) {
                        sb.append(sinkable);
                    }
                };
            }
        };

        Map<String, String> options = new HashMap<>();
        options.put(OptionsImpl.SHOW_CFR_VERSION.getName(), "false");
        options.put(OptionsImpl.TRACK_BYTECODE_LOC.getName(), "true");
        options.put(OptionsImpl.DECOMPILE_INNER_CLASSES.getName(), "true");
        options.put(OptionsImpl.DUMP_EXCEPTION_STACK_TRACE.getName(), "false");

        CfrDriver driver = new CfrDriver.Builder()
            .withOptions(options)
            .withOutputSink(sinkFactory)
            .build();
        driver.analyse(Arrays.asList(classFilePath.toAbsolutePath().toString()));

        String resultCode = sb.toString();
        if (!lineMapping.isEmpty()) {
            resultCode = addLineNumber(resultCode, lineMapping);
        }

        String expected = String.join("\n",
              "       /*\n"
            + "        * Decompiled with CFR.\n"
            + "        */\n"
            + "       package org.benf.cfr.tests;\n"
            + "       \n"
            + "       public class LineNumbersInnerClasses {\n"
            + "           public void method1() {\n"
            + "/*  6 */         System.out.println(\"Test.method1\");\n"
            + "           }\n"
            + "       \n"
            + "           public void method2() {\n"
            + "/* 10 */         System.out.println(\"Test.method2\");\n"
            + "           }\n"
            + "       \n"
            + "           public void method3() {\n"
            + "/* 29 */         System.out.println(\"Test.method3\");\n"
            + "           }\n"
            + "       \n"
            + "           public void method4() {\n"
            + "/* 33 */         System.out.println(\"Test.method4\");\n"
            + "           }\n"
            + "       \n"
            + "           public void method5() {\n"
            + "/* 67 */         System.out.println(\"Test.method5\");\n"
            + "           }\n"
            + "       \n"
            + "           public void method6() {\n"
            + "/* 71 */         System.out.println(\"Test.method6\");\n"
            + "           }\n"
            + "       \n"
            + "           class Inner4 {\n"
            + "               public Inner4() {\n"
            + "/* 54 */             System.out.println(\"Inner4 constructor\");\n"
            + "               }\n"
            + "       \n"
            + "               public void method1() {\n"
            + "/* 58 */             System.out.println(\"Inner4.method1\");\n"
            + "               }\n"
            + "       \n"
            + "               public void method2() {\n"
            + "/* 62 */             System.out.println(\"Inner4.method2\");\n"
            + "               }\n"
            + "           }\n"
            + "       \n"
            + "           class Inner3 {\n"
            + "               public Inner3() {\n"
            + "/* 39 */             System.out.println(\"Inner3 constructor\");\n"
            + "               }\n"
            + "       \n"
            + "               public void method1() {\n"
            + "/* 43 */             System.out.println(\"Inner3.method1\");\n"
            + "               }\n"
            + "       \n"
            + "               public void method2() {\n"
            + "/* 47 */             System.out.println(\"Inner3.method2\");\n"
            + "               }\n"
            + "           }\n"
            + "       \n"
            + "           class Inner1 {\n"
            + "               public Inner1() {\n"
            + "/* 16 */             System.out.println(\"Inner1 constructor\");\n"
            + "               }\n"
            + "       \n"
            + "               public void method1() {\n"
            + "/* 20 */             System.out.println(\"Inner1.method1\");\n"
            + "               }\n"
            + "       \n"
            + "               public void method2() {\n"
            + "/* 24 */             System.out.println(\"Inner1.method2\");\n"
            + "               }\n"
            + "           }\n"
            + "       }\n"
        );

        assertEquals(expected, resultCode);
    }

    private static String addLineNumber(String src, Map<Integer, Integer> lineMapping) {
        int maxLineNumber = 0;
        for (Integer value : lineMapping.values()) {
            if (value != null && value > maxLineNumber) {
                maxLineNumber = value;
            }
        }

        String formatStr = "/* %2d */ ";
        String emptyStr = "       ";

        StringBuilder sb = new StringBuilder();

        if (maxLineNumber >= 1000) {
            formatStr = "/* %4d */ ";
            emptyStr = "         ";
        } else if (maxLineNumber >= 100) {
            formatStr = "/* %3d */ ";
            emptyStr = "        ";
        }

        int index = 0;
        java.util.Scanner sc = new java.util.Scanner(src);
        try {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                Integer srcLineNumber = lineMapping.get(index + 1);
                if (srcLineNumber != null) {
                    sb.append(String.format(formatStr, srcLineNumber));
                } else {
                    sb.append(emptyStr);
                }
                sb.append(line).append("\n");
                index++;
            }
        } finally {
            sc.close();
        }
        return sb.toString();
    }
}