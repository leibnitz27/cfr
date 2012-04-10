package org.benf.cfr.tests;

import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * User: lee
 * Date: 05/05/2011
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
