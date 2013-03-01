package org.benf.cfr.tests;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 */
public class LoopTestTyped3 {

    public static <E> List<E> dup(List<E> arg) {
        return arg;
    }

    public void test5(List<String> lst) {

        List<String> lst2 = dup(lst);
        for (String s : lst2) {
            System.out.println(s);
        }
    }

}
