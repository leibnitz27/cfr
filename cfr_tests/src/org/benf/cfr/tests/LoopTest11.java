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
public class LoopTest11 {


    public int testNested(List<Object> list, Set<Object> set) {
        Iterator<Object> it1 = list.iterator();
        Iterator<Object> it2 = set.iterator();

        while (it1.hasNext() && it2.hasNext()) {
            it1.next();
            it2.next();
        }

        return 4;
    }


}
