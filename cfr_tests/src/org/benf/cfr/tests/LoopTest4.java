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
public class LoopTest4 {


    public void test5(int end) {
        int x = 0;
        do {
            if (x < end) continue;
            System.out.println(x);
        } while ((x = x + 2) < end);
    }


}
