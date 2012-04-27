package org.benf.cfr.reader.util;

/**
 * Created:
 * User: lee
 * Date: 27/04/2012
 */
public interface Predicate<X> {
    boolean test(X in);
}
