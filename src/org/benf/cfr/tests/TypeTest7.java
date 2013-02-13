package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class TypeTest7 {


    public boolean test1(int x) {
        return x % 2 == 0;
    }


    public boolean test2(int x) {
        boolean a = test1(x);
        if (!a) {
            a = test1(x);
        }
        return a;
    }

}
