package org.benf.cfr.tests;


import org.benf.cfr.tests.support.Functional;
import org.benf.cfr.tests.support.Predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * <p/>
 * NB: Gives an interesting example of pointless type generation!!!
 */
public class LoopTestTyped5 {

    public static List<Boolean> foo(List<Boolean> fooIn) {
        List<Boolean> res = Functional.filter(fooIn, new Predicate<Boolean>() {
            @Override
            public boolean test(Boolean in) {
                return true;
            }
        });
        for (Boolean b : res) {
            System.out.println(b);
        }
        return res;
    }

}
