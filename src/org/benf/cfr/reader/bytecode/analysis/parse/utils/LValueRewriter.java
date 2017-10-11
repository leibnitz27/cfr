package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;

import java.util.Set;

public interface LValueRewriter<T> {
    Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<T> statementContainer);

    boolean explicitlyReplaceThisLValue(LValue lValue);

    void checkPostConditions(LValue lValue, Expression rValue);

    LValueRewriter getWithFixed(Set fixed);
}
