package org.benf.cfr.reader.util.graph;

import org.benf.cfr.reader.util.functors.BinaryProcedure;

public class GraphVisitorFIFO<T> extends AbstractGraphVisitorFI<T> {
    public GraphVisitorFIFO(T first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        super(first, callee);
    }
}
