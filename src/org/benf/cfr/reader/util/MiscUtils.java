package org.benf.cfr.reader.util;

import java.util.regex.Pattern;

public class MiscUtils {
    public static <T> boolean xor(T a, T b, T required) {
        boolean b1 = a.equals(required);
        boolean b2 = b.equals(required);
        return b1 ^ b2;
    }

    public static Predicate<String> mkRegexFilter(String pat, boolean anywhere) {
        if (pat == null) {
            return new Predicate<String>() {
                @Override
                public boolean test(String in) {
                    return true;
                }
            };
        }

        if (anywhere) pat = "^.*" + pat + ".*$";
        final Pattern p = Pattern.compile(pat);
        return new Predicate<String>() {
            @Override
            public boolean test(String in) {
                return p.matcher(in).matches();
            }
        };
    }
}
