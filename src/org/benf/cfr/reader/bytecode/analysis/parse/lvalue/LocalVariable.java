package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:28
 */
public class LocalVariable extends AbstractLValue {
    private final String name;
    // We keep this so we don't confuse two variables with the same name, tricksy.
    private final long idx;

    public LocalVariable(long index, VariableNamer variableNamer, int originalRawOffset, InferredJavaType inferredJavaType) {
        super(inferredJavaType);
        this.name = variableNamer.getName(originalRawOffset, index);
        this.idx = index;
    }

    public LocalVariable(String name, InferredJavaType inferredJavaType) {
        super(inferredJavaType);
        this.name = name;
        this.idx = -1;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    /*
     * Can't modify, so deep clone is this.
     */
    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(name + typeToString());
    }

    public String getName() {
        return name;
    }

    @Override
    public <T> void collectLValueAssignments(Expression assignedTo, StatementContainer<T> statementContainer, LValueAssignmentCollector<T> lValueAssigmentCollector) {
        lValueAssigmentCollector.collectLocalVariableAssignment(this, statementContainer, assignedTo);
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalVariable)) return false;

        LocalVariable that = (LocalVariable) o;

        if (!name.equals(that.name)) return false;
        if (idx != that.idx) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) idx;
        return result;
    }
}
