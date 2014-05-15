package org.benf.cfr.reader.util.output;

public class CommaHelp {

    public static boolean dot(boolean first, StringBuilder sb) {
        if (!first) sb.append(".");
        return false;
    }

    public static boolean comma(boolean first, StringBuilder sb) {
        if (!first) sb.append(", ");
        return false;
    }

    public static boolean comma(boolean first, Dumper d) {
        if (!first) d.print(", ");
        return false;
    }
}
