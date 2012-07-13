package org.benf.cfr.reader.util;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 17:39
 */
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
