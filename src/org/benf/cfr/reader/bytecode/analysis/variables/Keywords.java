package org.benf.cfr.reader.bytecode.analysis.variables;

import java.util.*;

import org.benf.cfr.reader.util.collections.*;

public class Keywords {
    // from https://docs.oracle.com/javase/tutorial/java/nutsandbolts/_keywords.html
    // Keywords (mapped to potential replacements for variables of the same name,
    // if they are considered to be widely used).
    private static final Map<String, String> keywords = MapFactory.newMap();
    // (some are not keywords (see url), but literals you can't use.)
    static {
        // In general, keywords don't have a particularly common 'corresponding variable' name, however
        // some special ones (class -> clazz) are very well known.  For all others, tweak in a consistent way.
        keywords.put("abstract", null);
        keywords.put("assert", null);
        keywords.put("boolean", null);
        keywords.put("break", null);
        keywords.put("byte", "byteVal");
        keywords.put("case", null);
        keywords.put("catch", null);
        keywords.put("char", null);
        keywords.put("class", "clazz");
        keywords.put("const", null);
        keywords.put("continue", null);
        keywords.put("default", null);
        keywords.put("do", null);
        keywords.put("double", null);
        keywords.put("else", null);
        keywords.put("enum", null);
        keywords.put("extends", null);
        keywords.put("false", "fals");
        keywords.put("final", null);
        keywords.put("finally", null);
        keywords.put("float", null);
        keywords.put("for", null);
        keywords.put("goto", null);
        keywords.put("if", null);
        keywords.put("implements", null);
        keywords.put("import", null);
        keywords.put("instanceof", null);
        keywords.put("int", "intVal");
        keywords.put("interface", null);
        keywords.put("long", "longVal");
        keywords.put("native", null);
        keywords.put("new", null);
        keywords.put("null", "nullVal");
        keywords.put("package", null);
        keywords.put("private", null);
        keywords.put("protected", null);
        keywords.put("public", null);
        keywords.put("return", null);
        keywords.put("short", "shortVal");
        keywords.put("static", null);
        keywords.put("strictfp", null);
        keywords.put("super", null);
        keywords.put("switch", null);
        keywords.put("synchronized", null);
        keywords.put("this", "that");
        keywords.put("throw", null);
        keywords.put("throws", null);
        keywords.put("transient", null);
        keywords.put("true", "tru");
        keywords.put("try", null);
        keywords.put("void", null);
        keywords.put("volatile", null);
        keywords.put("while", null);
    }

    public static boolean isAKeyword(String string) {
        return keywords.containsKey(string);
    }

    public static String getReplacement(String keyword) {
        String rep = keywords.get(keyword);
        if (rep == null) {
            return keyword + "_";
        }
        return rep;
    }
}
