package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.variables.Ident;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariableDefault;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:28
 */
public class LocalVariable extends AbstractLValue {
    private final NamedVariable name;
    // We keep this so we don't confuse two variables with the same name, tricksy.
    private final int idx;
    private final Ident ident;
    private final boolean guessedFinal;

    public LocalVariable(int index, Ident ident, VariableNamer variableNamer, int originalRawOffset, InferredJavaType inferredJavaType, boolean guessedFinal) {
        super(inferredJavaType);
        this.name = variableNamer.getName(originalRawOffset, ident, index);
        this.idx = index;
        this.ident = ident;
        this.guessedFinal = guessedFinal;
    }

    public LocalVariable(String name, InferredJavaType inferredJavaType) {
        super(inferredJavaType);
        this.name = new NamedVariableDefault(name);
        this.idx = -1;
        this.ident = null;
        this.guessedFinal = false;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    public boolean isGuessedFinal() {
        return guessedFinal;
    }

    /*
         * Can't modify, so deep clone is this.
         */
    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.HIGHEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return name.dump(d).print(typeToString());
    }

    public NamedVariable getName() {
        return name;
    }

    public int getIdx() {
        return idx;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return false;
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
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        return new SSAIdentifiers<LValue>(this, ssaIdentifierFactory);
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
        if (ident == null) {
            if (that.ident != null) return false;
        } else {
            if (!ident.equals(that.ident)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) idx;
        if (ident != null) result = 31 * result + ident.hashCode();
        return result;
    }
}
