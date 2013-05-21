package org.benf.cfr.tests;

import java.io.IOException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class ExceptionSpecTest2 {

    public <X> List<X> func(X x) throws IllegalStateException {
        throw new IllegalStateException();
    }

    public <X> List<X> func2(X x) throws InnerException {
        throw new InnerException();
    }

    public static class InnerException extends RuntimeException {
    }
}
