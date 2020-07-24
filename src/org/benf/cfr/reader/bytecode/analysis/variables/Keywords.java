package org.benf.cfr.reader.bytecode.analysis.variables;

import java.util.*;

import org.benf.cfr.reader.util.collections.*;

public class Keywords {
    // from https://docs.oracle.com/javase/tutorial/java/nutsandbolts/_keywords.html
    private static final List<String> keywords =  ListFactory.newList(
            "abstract", "continue", "for", "new", "switch",
            "assert", "default", "goto", "package", "synchronized",
            "boolean", "do", "if", "private", "this",
            "break", "double", "implements", "protected", "throw",
            "byte", "else", "import", "public", "throws",
            "case", "enum", "instanceof", "return", "transient",
            "catch", "extends", "int", "short", "try",
            "char", "final", "interface", "static", "void",
            "class", "finally", "long", "strictfp", "volatile",
            "const", "float", "native", "super", "while",
            "true", "false", "null" // Not keywords (see url), but literals you can't use.
    );
    
    /**
     * Replacements for the keywords list, in order
     * Tries to use common given variable names
     */
    private static final List<String> replacements =  ListFactory.newList(
        "abstrct", "_continue", "_for", "_new", "zwitch",
        "asert", "deflt", "_goto", "pckg", "synched",
        "bool", "_do", "_if", "privt", "that",
        "_break", "doubleVal", "impl", "protctd", "_throw",
        "byteVal", "elze", "imprt", "pblc", "throwz",
        "caze", "enumVal", "instanzeof", "ret", "tranzient",
        "_catch", "extendz", "intVal", "shortVal", "_try",
        "_char", "_final", "interfaze", "_static", "_void",
        "clazz", "finaly", "longVal", "strict", "volatil",
        "constant", "floatVal", "_native", "_super", "_while",
        "tru", "fals", "nullVal" 
    );

    public static boolean isAKeyword(String string) {
        return keywords.contains(string);
    }

    public static String getReplacement(String keyword) {
        return replacements.get(keywords.indexOf(keyword));
    }
}
