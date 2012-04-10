package org.benf.cfr.tests;

import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class AssignTest2 {
    private static long sid = 0;

    private final long id0;

    public AssignTest2() {
        id0 = sid++;
    }

    public AssignTest2(boolean b) {
        id0 = ++sid;
    }

}
