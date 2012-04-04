package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.JVMInstr;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 06:34
 * To change this template use File | Settings | File Templates.
 */
public enum BoolOp {
    OR("||"),
    AND("&&");

    private final String showAs;

    private BoolOp(String showAs) {
        this.showAs = showAs;
    }

    public String getShowAs() {
        return showAs;
    }
}
