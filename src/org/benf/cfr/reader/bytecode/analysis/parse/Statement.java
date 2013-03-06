package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumpable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 17:52
 * To change this template use File | Settings | File Templates.
 */


/*
 * statement =
 * 
 *   assignment 
 *   if (condition) statement  [ else statement
 *   { list<statement> }
 *   label
 *   goto label
 */
public interface Statement extends Dumpable {
    void setContainer(StatementContainer container);

    void collectLValueAssignments(LValueAssignmentCollector lValueAssigmentCollector);

    void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers);

    void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers);

    void collectObjectCreation(CreationCollector creationCollector);

    SSAIdentifiers collectLocallyMutatedVariables(SSAIdentifierFactory ssaIdentifierFactory);

    boolean condenseWithNextConditional();

    boolean isCompound();

    boolean condenseWithPriorIfStatement(IfStatement ifStatement);

    // Valid to call on everything, only useful on an assignment.
    LValue getCreatedLValue();

    // Only sensible to call on an assignment
    Expression getRValue();

    StatementContainer getContainer();

    List<Statement> getCompoundParts();

    StructuredStatement getStructuredStatement();
}
