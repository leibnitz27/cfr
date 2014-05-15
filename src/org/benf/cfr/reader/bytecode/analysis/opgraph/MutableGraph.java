package org.benf.cfr.reader.bytecode.analysis.opgraph;

public interface MutableGraph<T> extends Graph<T> {
    void addSource(T source);
    void addTarget(T target);
}
