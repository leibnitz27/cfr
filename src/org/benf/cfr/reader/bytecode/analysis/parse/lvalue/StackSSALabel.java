package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 18:25
 * To change this template use File | Settings | File Templates.
 */
public class StackSSALabel extends AbstractLValue {
    private final long id;
    private final StackEntry stackEntry;

    public StackSSALabel(long id, StackEntry stackEntry) {
        super(stackEntry.getInferredJavaType());
        this.id = id;
        this.stackEntry = stackEntry;
    }

    /*
     * Only used for pattern matching.
     */
    protected StackSSALabel(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
        this.id = 0;
        this.stackEntry = null;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print("v" + id + typeToString());
    }

    @Override
    public int getNumberOfCreators() {
        return stackEntry.getSourceCount();
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return this;
    }

    /*
         * Can any use of this be replaced with the RHS instead?
         * (Assuming that values in the RHS are not mutated)
         */
    @Override
    public <Statement> void collectLValueAssignments(Expression rhsAssigned, StatementContainer<Statement> statementContainer, LValueAssignmentCollector<Statement> lValueAssigmentCollector) {
        if (getNumberOfCreators() == 1) {
            if ((rhsAssigned.isSimple() || stackEntry.getUsageCount() == 1)) {
                lValueAssigmentCollector.collect(this, statementContainer, rhsAssigned);
            } else if (stackEntry.getUsageCount() > 1) {
                lValueAssigmentCollector.collectMultiUse(this, statementContainer, rhsAssigned);
            }
        }
    }

    @Override
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        return new SSAIdentifiers();
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    public StackEntry getStackEntry() {
        return stackEntry;
    }

    @Override
    public int hashCode() {
        return (int) id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof StackSSALabel)) return false;
        return id == ((StackSSALabel) o).id;
    }

}
