package org.benf.cfr.tests.support;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 14:16
 */
public interface BinaryFunction<X, Y, R> {
    R invoke(X arg1, Y arg2);
}
