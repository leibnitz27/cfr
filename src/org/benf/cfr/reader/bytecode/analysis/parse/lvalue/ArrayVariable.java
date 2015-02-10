package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArrayIndex;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

public class ArrayVariable extends AbstractLValue {

    private ArrayIndex arrayIndex;

    public ArrayVariable(ArrayIndex arrayIndex) {
        super(arrayIndex.getInferredJavaType());
        this.arrayIndex = arrayIndex;
    }

    @Override
    public void markFinal() {

    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return new ArrayVariable((ArrayIndex) cloneHelper.replaceOrClone(arrayIndex));
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        arrayIndex.collectTypeUsages(collector);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        arrayIndex.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean doesBlackListLValueReplacement(LValue replace, Expression with) {
        return arrayIndex.doesBlackListLValueReplacement(replace, with);
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return arrayIndex.dump(d);
    }

    public ArrayIndex getArrayIndex() {
        return arrayIndex;
    }

    @Override
    public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        arrayIndex = (ArrayIndex) arrayIndex.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // Note ,we say as rvalue, as we're not changing the ARRAY. (bit dodgy this).
        arrayIndex = (ArrayIndex) arrayIndex.applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, ExpressionRewriterFlags.RVALUE);
        return this;
    }

    @Override
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
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
