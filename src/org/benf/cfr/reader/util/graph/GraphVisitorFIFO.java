package org.benf.cfr.reader.util.graph;

import org.benf.cfr.reader.util.functors.BinaryProcedure;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 13/03/2012
 * Time: 21:00
 * To change this template use File | Settings | File Templates.
 */
public class GraphVisitorFIFO<T> extends AbstractGraphVisitorFI<T> {
    public GraphVisitorFIFO(T first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        super(first, callee);
    }

    protected void internalAdd(T next) {
        toVisit.add(next);
    }
}
