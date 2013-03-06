package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/03/2013
 * Time: 05:55
 */
public interface LValueAssignmentCollector<T> {
    // TODO : Should these be StackSSALabels?  Seems they should be lvalues.
    void collect(StackSSALabel lValue, StatementContainer<T> statementContainer, Expression value);

    void collectMultiUse(StackSSALabel lValue, StatementContainer<T> statementContainer, Expression value);

    void collectMutatedLValue(LValue lValue, StatementContainer<T> statementContainer, Expression value);

    void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<T> statementContainer, Expression value);
}
