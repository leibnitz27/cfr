package org.benf.cfr.tests;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 */
public class TypeTest5<T> {


    public <Y extends String> void test1(Map<String, Y> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

    public <Y extends String> void test2(Map<String, ? extends Y> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

    public <Y extends String> void test3(Map<String, ? super Y> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

    public <Y extends String> void test4(Map<String, ? extends Y[]> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

    public <Y extends String> void test5(Map<String, ? extends String[]> x, T t) {
        StringBuilder sb = new StringBuilder();
        sb.append(x);
    }

}
