package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/09/2012
 * Time: 12:24
 */
public abstract class AbstractAssignment extends AbstractStatement {
    public abstract boolean isSelfMutatingOperation();

    public abstract boolean isSelfMutatingIncr1(LValue lValue);

    public abstract AbstractAssignmentExpression getInliningExpression();
}
