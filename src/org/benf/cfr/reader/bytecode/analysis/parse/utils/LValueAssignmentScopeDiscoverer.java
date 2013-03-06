package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/03/2013
 * Time: 06:09
 */
public class LValueAssignmentScopeDiscoverer implements LValueAssignmentCollector<StructuredStatement> {
    @Override
    public void collect(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }

    @Override
    public void collectMultiUse(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }

    @Override
    public void collectMutatedLValue(LValue lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }

    @Override
    public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }
}
