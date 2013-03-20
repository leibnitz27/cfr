package org.benf.cfr.reader.bytecode.analysis.opgraph;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 19/03/2013
 * Time: 18:01
 */
public interface Fold<T> {
    void add(T value);

    T getResult();
}
