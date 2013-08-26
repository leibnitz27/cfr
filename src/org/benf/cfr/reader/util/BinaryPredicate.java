package org.benf.cfr.reader.util;

/**
 * Created:
 * User: lee
 * Date: 27/04/2012
 */
public interface BinaryPredicate<X, Y> {
    boolean test(X a, Y b);
}
