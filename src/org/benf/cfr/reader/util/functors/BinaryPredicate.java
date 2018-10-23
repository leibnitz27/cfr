package org.benf.cfr.reader.util.functors;

public interface BinaryPredicate<X, Y> {
    boolean test(X a, Y b);
}
