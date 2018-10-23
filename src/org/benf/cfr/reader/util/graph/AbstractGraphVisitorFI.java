package org.benf.cfr.reader.util.graph;

import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

public abstract class AbstractGraphVisitorFI<T> implements GraphVisitor<T> {
    private final LinkedList<T> toVisit = ListFactory.newLinkedList();
    private final Set<T> visited = SetFactory.newSet();
    private final BinaryProcedure<T, GraphVisitor<T>> callee;
    private boolean aborted = false;

    AbstractGraphVisitorFI(T first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        add(first);
        this.callee = callee;
    }

    private void add(T next) {
        if (next == null) return;
        if (!visited.contains(next)) {
            toVisit.add(next);
            visited.add(next);
        }
    }

    @Override
    public void abort() {
        toVisit.clear();
        aborted = true;
    }

    @Override
    public boolean wasAborted() {
        return aborted;
    }

    @Override
    public Collection<T> getVisitedNodes() {
        return visited;
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
