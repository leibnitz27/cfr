package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class TypeTest1 {


    public void test1() {
        StringBuilder sb = new StringBuilder();
        char x = 'a';
        sb.append(x);
        sb.append((int) x);
    }

    public void test2() {
        StringBuilder sb = new StringBuilder();
        char x = 'a';
        sb.append((int) x);
        sb.append(x);
    }

    public void test3() {
        StringBuilder sb = new StringBuilder();
        int x = 97;
        sb.append(x);
        sb.append((char) x);
    }

    public void test4() {
        StringBuilder sb = new StringBuilder();
        int x = 97;
        sb.append((char) x);
        sb.append(x);
    }
}
