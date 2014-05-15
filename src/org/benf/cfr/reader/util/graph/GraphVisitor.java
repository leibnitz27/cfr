package org.benf.cfr.reader.util.graph;

import java.util.Collection;

public interface GraphVisitor<T> {

    void enqueue(T next);

    void enqueue(Collection<? extends T> next);

    void process();

    void abort();

    boolean wasAborted();

    Collection<T> getVisitedNodes();
}

