package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

public class Keywords {
    // from http://docs.oracle.com/javase/tutorial/java/nutsandbolts/_keywords.html
    private static final Set<String> keywords = SetFactory.newSet(
            "abstract", "continue", "for", "new", "switch",
            "assert", "default", "goto", "package", "synchronized",
            "boolean", "do", "if", "private", "this",
            "break", "double", "implements", "protected", "throw",
            "byte", "else", "import", "public", "throws",
            "case", "enum", "instanceof", "return", "transient",
            "catch", "extends", "int", "short", "try",
            "char", "final", "interface", "static", "void",
            "class", "finally", "long", "strictfp", "volatile",
            "const", "float", "native", "super", "while"
    );

    public static boolean isAKeyword(String string) {
        return keywords.contains(string);
    }
}
