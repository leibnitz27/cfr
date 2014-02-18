package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 16/12/2013
 * Time: 22:48
 */
public class QuotingUtils {
    public static String enquoteString(String s) {
        char[] raw = s.toCharArray();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\"");
        for (char c : raw) {
            switch (c) {
                case '\n':
                    stringBuilder.append("\\n");
                    break;
                case '\r':
                    stringBuilder.append("\\r");
                    break;
                case '\t':
                    stringBuilder.append("\\t");
                    break;
                case '\b':
                    stringBuilder.append("\\b");
                    break;
                case '\f':
                    stringBuilder.append("\\f");
                    break;
                case '\\':
                    stringBuilder.append("\\\\");
                    break;
                case '\"':
                    stringBuilder.append("\\\"");
                    break;
                default:
                    if (c < 32 || c > 127) {
                        stringBuilder.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        stringBuilder.append(c);
                    }
                    break;
            }
        }
        stringBuilder.append("\"");
        return stringBuilder.toString();
    }

    public static String enquoteIdentifier(String s) {
        char[] raw = s.toCharArray();
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : raw) {
            if (c < 32 || c > 127) {
                stringBuilder.append("\\u").append(String.format("%04x", (int) c));
            } else {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString();
    }

    public static String unquoteString(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }
}
