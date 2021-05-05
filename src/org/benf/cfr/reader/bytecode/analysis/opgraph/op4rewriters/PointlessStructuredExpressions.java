package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.PointlessExpressions;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;

public class PointlessStructuredExpressions {
    // Same as the op03 version, but we need to unpick the temporary we introduced.
    public static void removePointlessExpression(StructuredStatement stm) {
        if (stm instanceof StructuredAssignment) {
            StructuredAssignment ass = (StructuredAssignment)stm;
            LValue lv = ass.getLvalue();
            if (lv.isFakeIgnored()) {
                Expression e = ass.getRvalue();
                // This didn't used to be.   But after some rewriting it might now be.
                if (PointlessExpressions.isSafeToIgnore(e)) {
                    stm.getContainer().nopOut();
                }
            }
        }
    }
}
