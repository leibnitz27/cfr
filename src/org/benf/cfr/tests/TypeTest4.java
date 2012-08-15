package org.benf.cfr.tests;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 */
public class TypeTest4<T> {


    public void test1(Map<String, ?> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

    public void test2(Map<String, ? extends Object> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

    public void test3(Map<String, ? super Object> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

}
