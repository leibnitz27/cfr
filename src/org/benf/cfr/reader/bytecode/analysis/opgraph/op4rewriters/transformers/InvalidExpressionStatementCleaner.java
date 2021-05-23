package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;

import java.util.Set;

public class InvalidExpressionStatementCleaner extends AbstractExpressionRewriter implements StructuredStatementTransformer {

    private VariableFactory variableFactory;

    public InvalidExpressionStatementCleaner(VariableFactory variableNamer) {
        this.variableFactory = variableNamer;
    }

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        if (in instanceof StructuredExpressionStatement) {
            Expression exp = ((StructuredExpressionStatement) in).getExpression();
            if (!exp.isValidStatement()) {
                /* Have to assign to an ignored temporary, or discard.
                 * We prefer not to discard, as that involves detecting side effects
                 * and hides bytecode.
                 */
                return new StructuredAssignment(BytecodeLoc.TODO, variableFactory.ignoredVariable(exp.getInferredJavaType()), exp, true);
            }
        }
        return in;
    }
}
