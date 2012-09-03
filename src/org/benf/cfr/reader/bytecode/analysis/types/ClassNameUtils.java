package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 03/09/2012
 * Time: 18:00
 */
public class ClassNameUtils {
    public static String convert(String from) {
        return from.replace('/', '.');
    }
}
