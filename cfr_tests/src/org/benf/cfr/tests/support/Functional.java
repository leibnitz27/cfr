package org.benf.cfr.tests.support;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 17:07
 */
public class Functional {
    public static <X> List<X> filter(List<X> in, Predicate<X> p) {
        return in; // hey, it doesn't have to work...
    }
}
