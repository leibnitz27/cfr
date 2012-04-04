package org.benf.cfr.reader.util.graph;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;

import java.util.LinkedList;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 13/03/2012
 * Time: 06:10
 * To change this template use File | Settings | File Templates.
 */
public class GraphVisitorDFS<T> implements GraphVisitor<T> {
    private final T start;
    private final Set<T> visited = SetFactory.newSet();
    private final BinaryProcedure<T, GraphVisitor<T>> callee;
    
    public GraphVisitorDFS(T first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        this.start = first;
        this.callee = callee;
    }
    
    @Override
    public void enqueue(T next) {
        if (!visited.contains(next)) {
            visited.add(next);
            callee.call(next, this);
        }
    }
    
    @Override
    public void process() {
        enqueue(start);
    }
}
