package org.benf.cfr.reader.util;

import java.util.Collection;

public class CollectionUtils {
    public static String join(Collection<? extends Object> in, String sep) {
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
}
