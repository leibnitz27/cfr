package org.benf.cfr.tests.support;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 14:12
 */
public interface UnaryFunction<X,Y> {
    Y invoke(X arg);
}
