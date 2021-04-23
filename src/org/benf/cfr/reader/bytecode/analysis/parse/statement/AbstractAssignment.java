package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;

public abstract class AbstractAssignment extends AbstractStatement {

    public AbstractAssignment(BytecodeLoc loc) {
        super(loc);
    }

    public abstract boolean isSelfMutatingOperation();

    public abstract boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp);

    public abstract Expression getPostMutation();

    public abstract Expression getPreMutation();

    public abstract AbstractAssignmentExpression getInliningExpression();
}
