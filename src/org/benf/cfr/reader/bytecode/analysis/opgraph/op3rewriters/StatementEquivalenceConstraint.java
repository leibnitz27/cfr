package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ComparableUnderEC;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.DefaultEquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

public class StatementEquivalenceConstraint extends DefaultEquivalenceConstraint {

    private final SSAIdentifiers<LValue> ident1;
    private final SSAIdentifiers<LValue> ident2;

    public StatementEquivalenceConstraint(Op03SimpleStatement stm1, Op03SimpleStatement stm2) {
        ident1 = stm1.getSSAIdentifiers();
        ident2 = stm2.getSSAIdentifiers();
    }

    @Override
    public boolean equivalent(ComparableUnderEC o1, ComparableUnderEC o2) {
        if (o1 instanceof LValue && o2 instanceof LValue) {
            SSAIdent i1 = ident1.getSSAIdentOnEntry((LValue)o1);
            SSAIdent i2 = ident2.getSSAIdentOnEntry((LValue)o2);
            if (i1 == null) {
                if (i2 != null) return false;
            } else {
                if (!i1.equals(i2)) return false;
            }
        }
        return super.equivalent(o1, o2);
    }
}
