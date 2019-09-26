package org.benf.cfr.reader.util.collections;

import java.util.Collection;

public class CollectionUtils {
    public static String join(Collection<?> in, String sep) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : in) {
            if (first) {
                first = false;
            } else {
                sb.append(sep);
            }
            sb.append(o.toString());
        }
        return sb.toString();
    }

    public static String joinPostFix(Collection<?> in, String sep) {
        StringBuilder sb = new StringBuilder();
        for (Object o : in) {
            sb.append(o.toString());
            sb.append(sep);
        }
        return sb.toString();
    }
}
