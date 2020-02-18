package org.benf.cfr.reader.bytecode.analysis.parse.utils.scope;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public interface LValueScopeDiscoverer extends LValueUsageCollector, LValueAssignmentCollector<StructuredStatement> {
    void processOp04Statement(Op04StructuredStatement statement);

    void enterBlock(StructuredStatement structuredStatement);

    void leaveBlock(StructuredStatement structuredStatement);

    void mark(StatementContainer<StructuredStatement> mark);

    void collect(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collectMultiUse(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collectMutatedLValue(LValue lValue, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value);

    void collect(LValue lValue);

    boolean ifCanDefine();

    boolean descendLambdas();
}
