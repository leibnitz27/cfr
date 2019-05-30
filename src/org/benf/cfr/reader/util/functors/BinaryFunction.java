package org.benf.cfr.reader.util.functors;

public interface BinaryFunction<X,Y,Z> {
    Z invoke(X arg, Y arg2);
}
