package org.benf.cfr.reader.util.functors;

public interface TrinaryFunction<X, Y, Z, R> {
    R invoke(X arg1, Y arg2, Z arg3);
}
