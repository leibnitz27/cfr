package org.benf.cfr.reader.util.functors;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 13/03/2012
 * Time: 06:17
 * To change this template use File | Settings | File Templates.
 */
public interface BinaryProcedure<X,Y> {
    void call(X arg1, Y arg2);
}
