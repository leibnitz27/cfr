package org.benf.cfr.reader.bytecode.analysis.parse.utils.scope;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 27/02/2014
 * Time: 17:23
 */
public interface LValueScopeDiscoverer extends LValueUsageCollector, LValueAssignmentCollector<StructuredStatement> {
    void enterBlock(StructuredStatement structuredStatement);

    void leaveBlock(StructuredStatement structuredStatement);

    void collect(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collectMultiUse(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collectMutatedLValue(LValue lValue, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collect(LValue lValue);

}
