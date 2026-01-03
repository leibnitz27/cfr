/*
 * Decompiled with CFR.
 */
package org.benf.cfr.tests;

public class TypeArgTestCharIndex {
    private static final String TEST = "src/main/java/com/example/Test.java";

    public static void main(String[] stringArray) {
        System.out.println(TEST.substring(TEST.lastIndexOf('/') + 1, TEST.indexOf('.')));
    }
}
