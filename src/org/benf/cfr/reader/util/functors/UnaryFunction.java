package org.benf.cfr.reader.util.functors;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 12:21
 * To change this template use File | Settings | File Templates.
 */
public interface UnaryFunction<X,Y> {
    Y invoke(X arg);
}
