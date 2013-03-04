package org.benf.cfr.tests;

import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.Predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * <p/>
 * NB: Gives an interesting example of pointless type generation!!!
 */
public class LoopTestTyped6 {

    private final List<Boolean> lst = new ArrayList<Boolean>();

    public List<Boolean> foo() {
        for (Boolean b : lst) {
            System.out.println(b);
        }
        return lst;
    }

}
