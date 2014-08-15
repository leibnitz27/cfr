package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * This is NOT an lvalue, however the definition of a local class follows the same scoping rules
 */
public class SentinelLocalClassLValue extends AbstractLValue {
    private final JavaTypeInstance localClassType;

    public SentinelLocalClassLValue(JavaTypeInstance localClassType) {
        super(null);
        this.localClassType = localClassType;
    }

    @Override
    public void markFinal() {

    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        // nothing.
    }

    @Override
    public int getNumberOfCreators() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void collectLValueAssignments(Expression assignedTo, StatementContainer<T> statementContainer, LValueAssignmentCollector<T> lValueAssigmentCollector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.HIGHEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        throw new UnsupportedOperationException();
    }

    public JavaTypeInstance getLocalClassType() {
        return localClassType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SentinelLocalClassLValue that = (SentinelLocalClassLValue) o;

        if (localClassType != null ? !localClassType.equals(that.localClassType) : that.localClassType != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return localClassType != null ? localClassType.hashCode() : 0;
    }
}
