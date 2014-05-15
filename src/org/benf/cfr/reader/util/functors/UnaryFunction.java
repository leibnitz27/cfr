package org.benf.cfr.reader.util.functors;

public interface UnaryFunction<X,Y> {
    Y invoke(X arg);
}
