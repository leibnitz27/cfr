package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public class ArrayVariable extends AbstractLValue {

    private Expression arrayIndex;

    public ArrayVariable(Expression arrayIndex) {
        super(arrayIndex.getInferredJavaType());
        this.arrayIndex = arrayIndex;
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return new ArrayVariable(cloneHelper.replaceOrClone(arrayIndex));
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    @Override
    public Dumper dump(Dumper d) {
        return arrayIndex.dump(d);
    }

    @Override
    public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        arrayIndex = arrayIndex.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // Note ,we say as rvalue, as we're not changing the ARRAY. (bit dodgy this).
        arrayIndex = arrayIndex.applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, ExpressionRewriterFlags.RVALUE);
        return this;
    }

    @Override
    public SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory) {
        return new SSAIdentifiers();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArrayVariable)) return false;
        ArrayVariable other = (ArrayVariable) o;
        return arrayIndex.equals(other.arrayIndex);
    }
}
