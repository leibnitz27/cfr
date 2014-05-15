package org.benf.cfr.reader.util.functors;

public interface BinaryProcedure<X,Y> {
    void call(X arg1, Y arg2);
}
