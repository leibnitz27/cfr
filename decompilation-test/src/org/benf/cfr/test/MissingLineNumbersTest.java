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

class MissingLineNumbersTest {
    @Test
    void writesInnerClassLineNumbers() {
        Path classFilePath = Paths.get("decompilation-test", "test-data", "precompiled_tests", "java_8", "org", "benf", "cfr", "tests", "MissingLineNumbers.class");
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
            +"        * Decompiled with CFR.\n"
            +"        */\n"
            +"       package org.benf.cfr.tests;\n"
            +"       \n"
            +"       import java.text.SimpleDateFormat;\n"
            +"       import java.util.Date;\n"
            +"       import java.util.Map;\n"
            +"       \n"
            +"       public class MissingLineNumbers {\n"
            +"           public static Date parseDate(String s) {\n"
            +"               try {\n"
            +"/* 12 */             return new SimpleDateFormat(\"yyyy-MM-dd\").parse(s);\n"
            +"               } catch (Exception e) {\n"
            +"/* 14 */             throw new IllegalArgumentException(e);\n"
            +"               }\n"
            +"           }\n"
            +"       \n"
            +"           public static <K, V> void forEach(Map<K, V> map) {\n"
            +"/* 19 */         for (Map.Entry<K, V> entry : map.entrySet()) {\n"
            +"/* 20 */             System.out.println(entry);\n"
            +"               }\n"
            +"           }\n"
            +"       }\n"
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