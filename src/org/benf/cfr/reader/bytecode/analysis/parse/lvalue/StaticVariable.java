package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.util.output.Dumper;

public class StaticVariable extends AbstractFieldVariable {

    private final boolean knownSimple;

    public StaticVariable(ConstantPoolEntry field) {
        super(field);
        this.knownSimple = false;
    }

    /*
     * Used only for matching
     */
    public StaticVariable(InferredJavaType type, JavaTypeInstance clazz, String varName) {
        super(type, clazz, varName);
        this.knownSimple = false;
    }

    private StaticVariable(StaticVariable other, boolean knownSimple) {
        super(other);
        this.knownSimple = knownSimple;
    }

    /*
     * There are some circumstances (final assignment) where it's illegal to use the FQN of a static.
     */
    public StaticVariable getSimpleCopy() {
        return new StaticVariable(this, true);
    }


    @Override
    public Precedence getPrecedence() {
        return Precedence.HIGHEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (knownSimple) {
            return d.identifier(getFieldName());
        } else {
            return d.dump(getOwningClassType()).print(".").identifier(getFieldName());
        }
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof StaticVariable)) return false;
        if (!super.equals(o)) return false;
        StaticVariable other = (StaticVariable) o;
        return true;
//        return other.knownSimple == knownSimple;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
//        return super.hashCode() + (knownSimple ? 1 : 0) ;
    }
}
