package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class TypeTest2<T> {


    public <X extends Object, Y> void test1(X x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }
}
