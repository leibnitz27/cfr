package org.benf.cfr.tests;

import org.benf.cfr.reader.util.ListFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 */
public class LoopTestTyped1 {

    public void test5(String a, String b) {
        List<String> lst = new ArrayList<String>();
        lst.add(a);
        lst.add(b);

        for (String s : lst) {
            System.out.println(s);
        }
    }

}
