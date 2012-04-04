package org.benf.cfr.reader.bytecode.analysis.opgraph;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 19/03/2012
 * Time: 06:38
 * To change this template use File | Settings | File Templates.
 */
public interface MutableGraph<T> extends Graph<T> {
    void addSource(T source);
    void addTarget(T target);
}
