package org.benf.cfr.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 */
public class TypeTest3<T> {


    public <X extends Map<String, Integer>, Y> void test1(X x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }


    public <X extends HashMap<String, Integer[][]>, Y> void test1(X x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }
}
