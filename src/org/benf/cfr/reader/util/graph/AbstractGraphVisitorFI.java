package org.benf.cfr.reader.util.graph;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.UnaryProcedure;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 13/03/2012
 * Time: 06:10
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractGraphVisitorFI<T> implements GraphVisitor<T> {
    protected final LinkedList<T> toVisit = ListFactory.newLinkedList();
    private final Set<T> visited = SetFactory.newSet();
    private final BinaryProcedure<T, GraphVisitor<T>> callee;

    public AbstractGraphVisitorFI(T first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        add(first);
        this.callee = callee;
    }

    protected abstract void internalAdd(T next);

    private void add(T next) {
        if (!visited.contains(next)) {
            toVisit.add(next);
            visited.add(next);
        }
    }

    @Override
    public void enqueue(T next) {
        add(next);
    }

    @Override
    public void enqueue(Collection<? extends T> next) {
        for (T t : next) enqueue(t);
    }

    @Override
    public void process() {
        do {
            T next = toVisit.removeFirst();
            callee.call(next, this);
        } while (!toVisit.isEmpty());
    }
}
