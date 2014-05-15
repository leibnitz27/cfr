package org.benf.cfr.reader.util.functors;

public interface BinaryFunction<X, Y, R> {
    R invoke(X arg1, Y arg2);
}
