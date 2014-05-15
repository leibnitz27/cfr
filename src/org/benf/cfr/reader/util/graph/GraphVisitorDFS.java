package org.benf.cfr.reader.util.graph;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class GraphVisitorDFS<T> implements GraphVisitor<T> {
    private final Collection<? extends T> start;
    private final Set<T> visited = SetFactory.newSet();
    private final BinaryProcedure<T, GraphVisitor<T>> callee;
    private final LinkedList<T> pending = ListFactory.newLinkedList();
    private final LinkedList<T> enqueued = ListFactory.newLinkedList();
    private boolean aborted = false;

    public GraphVisitorDFS(T first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        this.start = ListFactory.newList(first);
        this.callee = callee;
    }

    public GraphVisitorDFS(Collection<? extends T> first, BinaryProcedure<T, GraphVisitor<T>> callee) {
        this.start = ListFactory.newList(first);
        this.callee = callee;
    }

    @Override
    public void enqueue(T next) {
        if (next == null) return;
        // These will be enqueued in the order they should be visited...
        enqueued.add(next);
    }

    @Override
    public void enqueue(Collection<? extends T> next) {
        for (T t : next) enqueue(t);
    }

    @Override
    public void abort() {
        enqueued.clear();
        pending.clear();
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
    public void process() {
        pending.clear();
        enqueued.clear();
        pending.addAll(start);
        while (!pending.isEmpty()) {
            T current = pending.removeFirst();
            if (!visited.contains(current)) {
                visited.add(current);
                callee.call(current, this);
                // Prefix pending with enqueued.
                while (!enqueued.isEmpty()) pending.addFirst(enqueued.removeLast());
            }
        }

    }
}
