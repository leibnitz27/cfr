package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionVisitor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ReadWrite;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Set;

public class LValueTypeClashCheck implements LValueScopeDiscoverer, StructuredStatementTransformer {

    private final Set<Integer> clashes = SetFactory.newSet();

    @Override
    public void processOp04Statement(Op04StructuredStatement statement) {
        statement.getStatement().traceLocalVariableScope(this);
    }

    @Override
    public void enterBlock(StructuredStatement structuredStatement) {
    }

    @Override
    public void leaveBlock(StructuredStatement structuredStatement) {
    }

    @Override
    public void mark(StatementContainer<StructuredStatement> mark) {
    }

    @Override
    public boolean ifCanDefine() {
        return false;
    }

    @Override
    public void collect(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {
        collectExpression(lValue, value);
    }

    @Override
    public void collectMultiUse(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {
        collectExpression(lValue, value);
    }

    @Override
    public void collectMutatedLValue(LValue lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {
        collectExpression(lValue, value);
    }

    @Override
    public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value) {
        collectExpression(localVariable, value);
    }

    public void collect(LValue lValue, ReadWrite rw) {
        collectExpression(lValue, null);
    }

    public void collectExpression(LValue lValue, Expression value) {
        lValue.collectLValueUsage(this);
        if (!(lValue instanceof LocalVariable)) {
            return;
        }
        int idx = ((LocalVariable) lValue).getIdx();
        InferredJavaType inferredJavaType = lValue.getInferredJavaType();
        if (inferredJavaType != null) {
            JavaTypeInstance javaTypeInstance = inferredJavaType.getJavaTypeInstance();
            if (inferredJavaType.isClash() || javaTypeInstance == RawJavaType.REF) {
                clashes.add(idx);
                return;
            }
            if (value != null) {
                StackType lStack = javaTypeInstance.getStackType();
                /*
                 * Most type clashes can be detected at the op2->op3 transformation stage.
                 * Ints are of course a pain, because bool/short/etc all use int at the jvm.
                 *
                 * We can usually correctly detect this too, because we've got return type
                 * /parameter type hints, however we can't always detect it if we hit literals.
                 * (we *could* check in useAsIsWithCasting, which would allow us to improve
                 * type hints, however, it would mean that we end up declaring int type as the
                 * smallest literal which would fit, which isn't great.)
                 */
                if (lStack == StackType.INT) {
                    JavaTypeInstance valueType = value.getInferredJavaType().getJavaTypeInstance();
                    if (valueType.getStackType() != StackType.INT) return;
                    if (!valueType.implicitlyCastsTo(javaTypeInstance, null)) {
                        clashes.add(idx);
                        return;
                    }
                    // Shouldn't happen!
                    if (!(javaTypeInstance instanceof RawJavaType)) return;
                    // Ok, but if it DOES, does the RHS fit?

                    Check check = new Check((RawJavaType)javaTypeInstance);
                    check.rewriteExpression(value, null, null, null);
                    if (!check.ok) {
                        clashes.add(idx);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.traceLocalVariableScope(this);
        in.transformStructuredChildren(this, scope);
        return in;
    }

    @Override
    public boolean descendLambdas() {
        return false;
    }

    public Set<Integer> getClashes() {
        return clashes;
    }

    private static class Check extends AbstractExpressionRewriter {
        private boolean ok = true;
        private RawJavaType javaTypeInstance;
        private Visitor visitor = new Visitor();

        Check(RawJavaType javaTypeInstance) {
            this.javaTypeInstance = javaTypeInstance;
        }

        private class Visitor extends AbstractExpressionVisitor<Void> {
            @Override
            public Void visit(Literal l) {
                // If the literal is out of range for the type we want, sad.
                if (!l.getValue().checkIntegerUsage(javaTypeInstance)) {
                    ok = false;
                }
                return null;
            }

            @Override
            public Void visit(TernaryExpression e) {
                rewriteExpression(e.getLhs(), null, null, null);
                rewriteExpression(e.getRhs(), null, null, null);
                return null;
            }

            @Override
            public Void visit(ArithmeticOperation e) {
                if (!e.getOp().isBoolSafe() && javaTypeInstance == RawJavaType.BOOLEAN) {
                    ok = false;
                    return null;
                }
                e.applyExpressionRewriter(Check.this, null, null, null);
                return null;
            }
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            expression.visit(visitor);
            return expression;
        }
    }
}
