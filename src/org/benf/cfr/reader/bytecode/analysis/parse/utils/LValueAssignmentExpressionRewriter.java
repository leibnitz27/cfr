package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/07/2012
 * Time: 18:13
 */
public class LValueAssignmentExpressionRewriter implements LValueRewriter {

    private final LValue lValue;
    private final AssignmentExpression lValueReplacement;
    private final Op03SimpleStatement source;

    public LValueAssignmentExpressionRewriter(LValue lValue, AssignmentExpression lValueReplacement, Op03SimpleStatement source) {
        this.lValue = lValue;
        this.lValueReplacement = lValueReplacement;
        this.source = source;
    }

    @Override
    public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        if (!lValue.equals(this.lValue)) return null;
        if (!ssaIdentifiers.isValidReplacement(lValue, statementContainer.getSSAIdentifiers())) return null;
        source.nopOut();
        return lValueReplacement;
    }

    @Override
    public boolean explicitlyReplaceThisLValue(LValue lValue) {
        return lValue.equals(this.lValue);
    }
}
