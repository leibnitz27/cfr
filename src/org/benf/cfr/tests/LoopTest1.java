package org.benf.cfr.tests;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest1 {


    public boolean test1(List<Object> list, Set<Object> set) {
        boolean result = false;
        for (Object o : list) {
            result |= set.add(o);
        }
        return result;
    }


    public boolean test2(List<Object> list, Set<Object> set) {
        boolean result = false;
        Iterator<Object> e = list.iterator();
        while (e.hasNext()) {
            if (set.add(e.next())) {
                result = true;
            }
        }
        return result;
    }

    public void test3(int end) {
        for (int x = 0; x < end; ++x) {
            System.out.println(x);
        }
    }

    public void test3a(int[] xs) {
        for (int x : xs) {
            System.out.println(x);
        }
    }

    public void test4(int end) {
        int x = 0;
        while (x < end) {
            System.out.println(x);
            x++;
        }
    }

    public void test4a(int end) {
        int x = 0;
        while (x < end) {
            System.out.println(x);
            if (x > 5) break;
            if (x < 10) {
                x++;
                continue;
            }
            x++;
        }
    }

    public void test5(int end) {
        int x = 0;
        do {
            System.out.println(x);
            x++;
        } while (x < end);
    }


}
