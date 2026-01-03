package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;

import java.util.List;

/*
 * Lot of common code in here and static function invokation.
 */
public abstract class AbstractMemberFunctionInvokation extends AbstractFunctionInvokation implements FunctionProcessor, BoxingProcessor {
    private final ConstantPool cp;
    private final List<Expression> args;
    private Expression object;
    private final List<Boolean> nulls;

    AbstractMemberFunctionInvokation(BytecodeLoc loc, ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, JavaTypeInstance bestType, List<Expression> args, List<Boolean> nulls) {
        super(loc, function,
                new InferredJavaType(
                function.getMethodPrototype().getReturnType(
                        bestType, args
                ), InferredJavaType.Source.FUNCTION, true
        ));
        this.object = object;
        this.args = args;
        this.nulls = nulls;
        this.cp = cp;
    }

    AbstractMemberFunctionInvokation(BytecodeLoc loc, ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, List<Expression> args, List<Boolean> nulls) {
        this(loc, cp, function, object, object.getInferredJavaType().getJavaTypeInstance(), args, nulls);
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, args);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (Expression arg : args) arg.collectTypeUsages(collector);
        getMethodPrototype().collectTypeUsages(collector);
        collector.collectFrom(object);
        super.collectTypeUsages(collector);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        if (lValueRewriter.needLR()) {
            object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
            LValueRewriter.Util.rewriteArgArray(lValueRewriter, ssaIdentifiers, statementContainer, args);
        } else {
            LValueRewriter.Util.rewriteArgArray(lValueRewriter, ssaIdentifiers, statementContainer, args);
            object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        applyExpressionRewriterToArgs(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void applyExpressionRewriterToArgs(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    // Ignored, for now.
    @Override
    public void setExplicitGenerics(List<JavaTypeInstance> types) {
    }

    @Override
    public List<JavaTypeInstance> getExplicitGenerics() {
        return null;
    }

    public Expression getObject() {
        return object;
    }

    public JavaTypeInstance getClassTypeInstance() {
        return getFunction().getClassEntry().getTypeInstance();
    }

    public List<Expression> getArgs() {
        return args;
    }

    public List<Boolean> getNulls() {
        return nulls;
    }

    public Expression getAppropriatelyCastArgument(int idx) {
        return getMethodPrototype().getAppropriatelyCastedArgument(args.get(idx), idx);
    }

    public ConstantPool getCp() {
        return cp;
    }


    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        object.collectUsedLValues(lValueUsageCollector);
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

    private OverloadMethodSet getOverloadMethodSet() {
        JavaTypeInstance objectType = object.getInferredJavaType().getJavaTypeInstance();
        OverloadMethodSet overloadMethodSet = getOverloadMethodSetInner(objectType);
        if (overloadMethodSet == null) {
            overloadMethodSet = getMethodPrototype().getOverloadMethodSet();
        }
        if (overloadMethodSet == null) return null;
        if (objectType instanceof JavaGenericRefTypeInstance) {
            JavaGenericRefTypeInstance genericType = (JavaGenericRefTypeInstance) objectType;
            return overloadMethodSet.specialiseTo(genericType);
        }
        return overloadMethodSet;
    }

    protected OverloadMethodSet getOverloadMethodSetInner(JavaTypeInstance objectType) {
        JavaTypeInstance deGenerifiedObjectType = objectType.getDeGenerifiedType();
        // Could well be null....
        JavaTypeInstance protoClassType = getFunction().getMethodPrototype().getClassType();
        if (protoClassType == null || deGenerifiedObjectType != protoClassType.getDeGenerifiedType()) {
            // TODO : This is more expensive than I'd like.
            OverloadMethodSet overloadMethodSet = getMethodPrototype().getOverloadMethodSet();
            if (deGenerifiedObjectType instanceof JavaRefTypeInstance) {
                ClassFile classFile = ((JavaRefTypeInstance) deGenerifiedObjectType).getClassFile();
                if (classFile != null) {
                    overloadMethodSet = classFile.getOverloadMethodSet(getMethodPrototype());
                }
            }
            return overloadMethodSet;
        }
        return null;
    }

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        MethodPrototype methodPrototype = getMethodPrototype();
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, args, gtb);
    }


    private Expression insertCastOrIgnore(Expression arg, OverloadMethodSet overloadMethodSet, int x) {
        JavaTypeInstance argType = overloadMethodSet.getArgType(x, arg.getInferredJavaType().getJavaTypeInstance());
        boolean ignore = false;
        if (argType instanceof JavaGenericBaseInstance) {
            // TODO : Should check flag for ignore bad generics?
            ignore = ((JavaGenericBaseInstance) argType).hasForeignUnbound(cp, 0, false, null);
        }
        /*
         * Lambda types will always look wrong.
         */
        if (!ignore) {
            ignore = arg instanceof LambdaExpression ||
                     arg instanceof LambdaExpressionFallback;
        }
        if (!ignore) {
            return new CastExpression(BytecodeLoc.NONE, new InferredJavaType(argType, InferredJavaType.Source.EXPRESSION, true), arg);
        }
        return arg;
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        if (args.isEmpty()) return false;
        /*
         * Ignore completely for lambda, etc.
         */
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) {
            boxingRewriter.removeRedundantCastOnly(args);
            return false;
        }

        MethodPrototype methodPrototype = getMethodPrototype();
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);

        boolean callsCorrectEntireMethod = overloadMethodSet.callsCorrectEntireMethod(args, gtb);
        boolean nullsPresent = false;
        for (int x = 0; x < args.size(); ++x) {
            /*
             * We can only remove explicit boxing if the target type is correct -
             * i.e. calling an object function with an explicit box can't have the box removed.
             *
             * This is fixed by a later pass which makes sure that the argument
             * can be passed to the target.
             */
            Expression arg = args.get(x);
            /*
             * we only need to shove a cast to the exact type on it if our current argument
             * doesn't call the 'correct' method.
             */
            if (!callsCorrectEntireMethod && !overloadMethodSet.callsCorrectMethod(arg, x, gtb)) {
                /*
                 * If arg isn't the right type, shove an extra cast on the front now.
                 * Then we will forcibly remove it if we don't need it.
                 */
                arg = insertCastOrIgnore(arg, overloadMethodSet, x);
            }

            arg = boxingRewriter.rewriteExpression(arg, null, null, null);
            arg = boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet, gtb, methodPrototype);
            nullsPresent |= isResolveNull(arg);
            args.set(x, arg);
        }
        if (nullsPresent) {
            callsCorrectEntireMethod = overloadMethodSet.callsCorrectEntireMethod(args, gtb);
            if (!callsCorrectEntireMethod) {
                for (int x = 0; x < args.size(); ++x) {
                    Expression arg = args.get(x);
                    if (isResolveNull(arg)) {
                        arg = insertCastOrIgnore(arg, overloadMethodSet, x);
                        args.set(x, arg);
                    }
                }
            }
        }

        return true;
    }

    private static boolean isResolveNull(Expression arg) {
        return (Literal.NULL.equals(arg)) || arg.getInferredJavaType().getJavaTypeInstance() == RawJavaType.NULL;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
    }

    protected Expression getArgForStringIndexOfCharLiteral(int argIndex, Expression arg) {
        if (argIndex != 0) return arg;
        String name = getMethodPrototype().getName();
        if (!"indexOf".equals(name) && !"lastIndexOf".equals(name)) return arg;
        JavaTypeInstance classType = getFunction().getClassEntry().getTypeInstance();
        if (!TypeConstants.STRING.equals(classType.getDeGenerifiedType())) return arg;
        if (!(arg instanceof Literal)) return arg;
        Literal literal = (Literal) arg;
        TypedLiteral typed = literal.getValue();
        if (typed.getType() != TypedLiteral.LiteralType.Integer) return arg;
        if (typed.getInferredJavaType().getRawType() != RawJavaType.INT) return arg;
        int value = typed.getIntValue();
        if (value < Character.MIN_VALUE || value > Character.MAX_VALUE) return arg;
        return new Literal(TypedLiteral.getChar(value));
    }


    /*
     * We can be SLIGHTLY clever here.  If only checked exceptions are being caught, we
     * can see if our target is declared as throwing one of these.  Otherwise, if non-checked
     * are being caught, we should always consider as throwing.
     */
    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return caught.checkAgainst(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof AbstractMemberFunctionInvokation)) return false;
        AbstractMemberFunctionInvokation other = (AbstractMemberFunctionInvokation) o;
        if (!object.equals(other.object)) return false;
        if (!args.equals(other.args)) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof AbstractMemberFunctionInvokation)) return false;
        AbstractMemberFunctionInvokation other = (AbstractMemberFunctionInvokation) o;
        if (!constraint.equivalent(object, other.object)) return false;
        if (!constraint.equivalent(args, other.args)) return false;
        return true;
    }
}
