package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;

public class LValueAssignmentExpressionRewriter implements LValueRewriter<Statement> {

    private final LValue lValue;
    private final AbstractAssignmentExpression lValueReplacement;
    private final Op03SimpleStatement source;

    public LValueAssignmentExpressionRewriter(LValue lValue, AbstractAssignmentExpression lValueReplacement, Op03SimpleStatement source) {
        this.lValue = lValue;
        this.lValueReplacement = lValueReplacement;
        this.source = source;
    }

    @Override
    public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<Statement> statementContainer) {
        if (!lValue.equals(this.lValue)) return null;
        if (!ssaIdentifiers.isValidReplacement(lValue, statementContainer.getSSAIdentifiers())) return null;
        source.nopOut();
        return lValueReplacement;
    }

    @Override
    public void checkPostConditions(LValue lValue, Expression rValue) {

    }

    @Override
    public boolean explicitlyReplaceThisLValue(LValue lValue) {
        return lValue.equals(this.lValue);
    }
}
