package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.getopt.CFRState;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2013
 * Time: 06:26
 */
public class SyntheticAccessorRewriter implements Op04Rewriter, ExpressionRewriter {

    private final CFRState cfrState;
    private final JavaTypeInstance thisClassType;

    public SyntheticAccessorRewriter(CFRState cfrState, JavaTypeInstance thisClassType) {
        this.cfrState = cfrState;
        this.thisClassType = thisClassType;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        for (StructuredStatement statement : structuredStatements) {
            statement.rewriteExpressions(this);
        }
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // TODO : In practice, the rewrites are ALWAYS done in terms of static functions.
        // TODO : should we assume this?  Seems like a good thing an obfuscator could use.
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof StaticFunctionInvokation) {
            /*
             * REWRITE INSIDE OUT! First, rewrite args, THEN rewrite expression.
             */
            return rewriteFunctionExpression((StaticFunctionInvokation) expression);
        }
        return expression;
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (AbstractAssignmentExpression) res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    private Expression rewriteFunctionExpression(final StaticFunctionInvokation functionInvokation) {
        JavaTypeInstance tgtType = functionInvokation.getClazz();
        // Does tgtType have an inner relationship with this?
        boolean child = thisClassType.getInnerClassHereInfo().isInnerClassOf(tgtType);
        boolean parent = tgtType.getInnerClassHereInfo().isInnerClassOf(thisClassType);
        if (!(child || parent)) return functionInvokation;

        ClassFile otherClass = cfrState.getClassFile(tgtType, true);
        MethodPrototype otherPrototype = functionInvokation.getFunction().getMethodPrototype();
        List<Expression> appliedArgs = functionInvokation.getArgs();
        /*
         * For an INSTANCE accessor,
         * We require that the first argument to this method is an instance of the other class.
         *
         * (and that we're passing it something which is marked as the synthetic outer member
         * mapping to that class).
         */
        List<JavaTypeInstance> otherArgs = otherPrototype.getArgs();
        boolean mutatingAccessor = false;
        switch (otherArgs.size()) {
            case 2:
                mutatingAccessor = true;
                // fallthrough.
            case 1:
                if (!tgtType.equals(otherArgs.get(0))) return functionInvokation;
                if (!tgtType.equals(appliedArgs.get(0).getInferredJavaType().getJavaTypeInstance()))
                    return functionInvokation;
                break;
            default:
                return functionInvokation;
        }

        Method otherMethod = null;
        try {
            otherMethod = otherClass.getMethodByPrototype(otherPrototype);
        } catch (NoSuchMethodException e) {
            // Ignore and return.
        }
        if (otherMethod == null) return functionInvokation;

        if (!otherMethod.testAccessFlag(AccessFlagMethod.ACC_SYNTHETIC)) return functionInvokation;

        Op04StructuredStatement otherCode = otherMethod.getAnalysis();
        if (otherCode == null) return functionInvokation;

        /*
         * If otherCode maps down to a single expression (or a single one inside a block) we can inline it,
         * after replacing the argument.
         *
         * Fortunately (!) everywhere this can be used, it will always be referred to as 'Fred.this', so we can
         * actually rewrite the source class!!
         *
         * HOWEVER - if it's a mutating accessor, we can't do that, as we would end up inlining a fixed version
         * of the mutation.
         *
         * (NB : Strictly speaking, we should check for use of OTHER source data to which our inner class doesn't
         * have access as a criteria for inlining.)
         */
        StructuredStatement otherRoot = otherCode.getStatement();
        if (!(otherRoot instanceof Block)) return functionInvokation;

        Block otherBlock = (Block) otherRoot;
        // Ok, just a getter.  Simpler.
        if (!mutatingAccessor) return rewriteGetterAccessor(functionInvokation, otherMethod, otherBlock);

        // Mutating - so we need to inline the mutation.
        return rewriteMutatingAccessor(functionInvokation, otherMethod, otherBlock);
    }

    private Expression rewriteMutatingAccessor(final StaticFunctionInvokation functionInvokation, Method targetMethod, Block block) {
        List<Op04StructuredStatement> containedStatements = block.getBlockStatements();
        containedStatements = Functional.filter(containedStatements, new Predicate<Op04StructuredStatement>() {
            @Override
            public boolean test(Op04StructuredStatement in) {
                return !(in.getStatement() instanceof StructuredComment);
            }
        });

        if (containedStatements.size() != 2) return functionInvokation;

        /*
         * Should probably use a code matcher here, but I can't be bothered right now.
         */

        // We need statement 1 to be [a] = [b], 2 to be return [a]
        StructuredStatement s1 = containedStatements.get(0).getStatement();
        StructuredStatement s2 = containedStatements.get(1).getStatement();

        if (!(s1 instanceof StructuredAssignment)) return functionInvokation;
        StructuredAssignment sa1 = (StructuredAssignment) s1;
        if (!(s2 instanceof StructuredReturn)) return functionInvokation;
        StructuredReturn sr2 = (StructuredReturn) s2;

        Expression e2 = sr2.getValue();
        if (!(e2 instanceof LValueExpression)) return functionInvokation;
        LValue lr = ((LValueExpression) e2).getLValue();

        if (!(((StructuredAssignment) s1).getLvalue().equals(lr))) return functionInvokation;

        List<Expression> appliedArgs = functionInvokation.getArgs();
        List<LocalVariable> fnArgs = targetMethod.getMethodPrototype().getParameters();

        Expression applied0 = appliedArgs.get(0);
        if (!(applied0 instanceof LValueExpression)) return functionInvokation;
        LValue appliedLValue = ((LValueExpression) applied0).getLValue();

        Map<LValue, LValue> lValueReplacements = MapFactory.newMap();
        lValueReplacements.put(fnArgs.get(0), appliedLValue);

        LValueExpression arg1 = new LValueExpression(fnArgs.get(1));
        Map<Expression, Expression> expressonReplacements = MapFactory.newMap();
        expressonReplacements.put(arg1, appliedArgs.get(1));

        AssignmentExpression assignmentExpression = new AssignmentExpression(sa1.getLvalue(), sa1.getRvalue(), false);
        Expression resultExpression = assignmentExpression;

        CloneHelper cloneHelper = new CloneHelper(expressonReplacements, lValueReplacements);
        resultExpression = cloneHelper.replaceOrClone(resultExpression);
        targetMethod.hideSynthetic();
        return resultExpression;
    }

    private Expression rewriteGetterAccessor(final StaticFunctionInvokation functionInvokation, Method targetMethod, Block block) {
        if (!block.isJustOneStatement()) return functionInvokation;

        StructuredStatement statementToInline = block.getSingleStatement().getStatement();

        if (!(statementToInline instanceof StructuredReturn)) return functionInvokation;

        Expression resultExpression = ((StructuredReturn) statementToInline).getValue();

        List<Expression> appliedArgs = functionInvokation.getArgs();
        List<LocalVariable> fnArgs = targetMethod.getMethodPrototype().getParameters();
        /*
         * Clone resultExpression, and replace the first (only) arg with args[0].
         * (we could get away with modifying in place, but that's ... so horrible.).
         */
        Expression applied0 = appliedArgs.get(0);
        if (!(applied0 instanceof LValueExpression)) return functionInvokation;
        LValue appliedLValue = ((LValueExpression) applied0).getLValue();

        Map<Expression, Expression> expressonReplacements = MapFactory.newMap();
        Map<LValue, LValue> lValueReplacements = MapFactory.newMap();
        lValueReplacements.put(fnArgs.get(0), appliedLValue);
        CloneHelper cloneHelper = new CloneHelper(expressonReplacements, lValueReplacements);
        resultExpression = cloneHelper.replaceOrClone(resultExpression);
        targetMethod.hideSynthetic();
        return resultExpression;
    }
}
