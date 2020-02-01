package org.benf.cfr.reader.bytecode.analysis.parse.wildcard;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionVisitor;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.Optional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;

/**
 * This is all horribly messy and needs refactoring.
 */
public class WildcardMatch {

    /*
     * This could probably be done with one map....
     */
    private Map<String, LValueWildcard> lValueMap = MapFactory.newMap();
    private Map<String, StackLabelWildCard> lStackValueMap = MapFactory.newMap();
    private Map<String, ExpressionWildcard> expressionMap = MapFactory.newMap();
    private Map<String, NewArrayWildcard> newArrayWildcardMap = MapFactory.newMap();
    private Map<String, MemberFunctionInvokationWildcard> memberFunctionMap = MapFactory.newMap();
    private Map<String, SuperFunctionInvokationWildcard> superFunctionMap = MapFactory.newMap();
    private Map<String, StaticFunctionInvokationWildcard> staticFunctionMap = MapFactory.newMap();
    private Map<String, BlockIdentifierWildcard> blockIdentifierWildcardMap = MapFactory.newMap();
    private Map<String, ListWildcard> listMap = MapFactory.newMap();
    private Map<String, StaticVariableWildcard> staticVariableWildcardMap = MapFactory.newMap();
    private Map<String, ArithmeticMutationWildcard> arithMutationMap = MapFactory.newMap();
    private Map<String, ConstructorInvokationSimpleWildcard> constructorWildcardMap = MapFactory.newMap();
    private Map<String, ConstructorInvokationAnonymousInnerWildcard> constructorAnonymousWildcardMap = MapFactory.newMap();
    private Map<String, CastExpressionWildcard> castWildcardMap = MapFactory.newMap();
    private Map<String, ConditionalExpressionWildcard> conditionalWildcardMap = MapFactory.newMap();
    private Map<String, BlockWildcard> blockWildcardMap = MapFactory.newMap();

    private <T> void reset(Collection<? extends Wildcard<T>> coll) {
        for (Wildcard<T> item : coll) {
            item.resetMatch();
        }
    }

    public void reset() {
        reset(lValueMap.values());
        reset(lStackValueMap.values());
        reset(expressionMap.values());
        reset(newArrayWildcardMap.values());
        reset(memberFunctionMap.values());
        reset(blockIdentifierWildcardMap.values());
        reset(listMap.values());
        reset(staticFunctionMap.values());
        reset(staticVariableWildcardMap.values());
        reset(superFunctionMap.values());
        reset(constructorWildcardMap.values());
        reset(constructorAnonymousWildcardMap.values());
        reset(castWildcardMap.values());
        reset(conditionalWildcardMap.values());
        reset(blockWildcardMap.values());
        reset(arithMutationMap.values());
    }

    public BlockWildcard getBlockWildcard(String name) {
        BlockWildcard res = blockWildcardMap.get(name);
        if (res != null) return res;

        res = new BlockWildcard();
        blockWildcardMap.put(name, res);
        return res;
    }

    public StackLabelWildCard getStackLabelWildcard(String name) {
        StackLabelWildCard res = lStackValueMap.get(name);
        if (res != null) return res;

        res = new StackLabelWildCard();
        lStackValueMap.put(name, res);
        return res;
    }

    public ConditionalExpressionWildcard getConditionalExpressionWildcard(String name) {
        ConditionalExpressionWildcard res = conditionalWildcardMap.get(name);
        if (res != null) return res;

        res = new ConditionalExpressionWildcard();
        conditionalWildcardMap.put(name, res);
        return res;
    }

    public ArithmeticMutationWildcard getArithmeticMutationWildcard(String name) {
        return getArithmeticMutationWildcard(name, Optional.<LValue>empty(), Optional.<Expression>empty(), Optional.<ArithOp>empty());
    }

    public ArithmeticMutationWildcard getArithmeticMutationWildcard(String name, LValue lhs, Expression rhs) {
        return getArithmeticMutationWildcard(name, Optional.of(lhs), Optional.of(rhs), Optional.<ArithOp>empty());
    }

    private ArithmeticMutationWildcard getArithmeticMutationWildcard(String name,   Optional<LValue> lhs, Optional<Expression> rhs, Optional<ArithOp> op) {
        ArithmeticMutationWildcard res = arithMutationMap.get(name);
        if (res != null) return res;
        res = new ArithmeticMutationWildcard(lhs, rhs, op);
        arithMutationMap.put(name, res);
        return res;
    }

    public ConstructorInvokationSimpleWildcard getConstructorSimpleWildcard(String name) {
        ConstructorInvokationSimpleWildcard res = constructorWildcardMap.get(name);
        if (res != null) return res;

        res = new ConstructorInvokationSimpleWildcard(null, null);
        constructorWildcardMap.put(name, res);
        return res;
    }

    public ConstructorInvokationSimpleWildcard getConstructorSimpleWildcard(String name, JavaTypeInstance clazz) {
        ConstructorInvokationSimpleWildcard res = constructorWildcardMap.get(name);
        if (res != null) return res;

        res = new ConstructorInvokationSimpleWildcard(clazz, null);
        constructorWildcardMap.put(name, res);
        return res;
    }

    public ConstructorInvokationAnonymousInnerWildcard getConstructorAnonymousWildcard(String name) {
        ConstructorInvokationAnonymousInnerWildcard res = constructorAnonymousWildcardMap.get(name);
        if (res != null) return res;

        res = new ConstructorInvokationAnonymousInnerWildcard(null, null);
        constructorAnonymousWildcardMap.put(name, res);
        return res;
    }

    public ConstructorInvokationAnonymousInnerWildcard getConstructorAnonymousWildcard(String name, JavaTypeInstance clazz) {
        ConstructorInvokationAnonymousInnerWildcard res = constructorAnonymousWildcardMap.get(name);
        if (res != null) return res;

        res = new ConstructorInvokationAnonymousInnerWildcard(clazz, null);
        constructorAnonymousWildcardMap.put(name, res);
        return res;
    }


    public LValueWildcard getLValueWildCard(String name, Predicate<LValue> test) {
        LValueWildcard res = lValueMap.get(name);
        if (res != null) return res;

        res = new LValueWildcard(test);
        lValueMap.put(name, res);
        return res;
    }

    public LValueWildcard getLValueWildCard(String name) {
        LValueWildcard res = lValueMap.get(name);
        if (res != null) return res;

        res = new LValueWildcard(null);
        lValueMap.put(name, res);
        return res;
    }

    public ExpressionWildcard getExpressionWildCard(String name) {
        ExpressionWildcard res = expressionMap.get(name);
        if (res != null) return res;

        res = new ExpressionWildcard();
        expressionMap.put(name, res);
        return res;
    }

    public CastExpressionWildcard getCastExpressionWildcard(String name, Expression expression) {
        CastExpressionWildcard res = castWildcardMap.get(name);
        if (res != null) return res;

        res = new CastExpressionWildcard(null, expression);
        castWildcardMap.put(name, res);
        return res;
    }

    public NewArrayWildcard getNewArrayWildCard(String name) {
        return getNewArrayWildCard(name, 1, null);
    }

    public NewArrayWildcard getNewArrayWildCard(String name, int numSizedDims, Integer numTotalDims) {
        NewArrayWildcard res = newArrayWildcardMap.get(name);
        if (res != null) return res;

        res = new NewArrayWildcard(numSizedDims, numTotalDims);
        newArrayWildcardMap.put(name, res);
        return res;

    }

    public SuperFunctionInvokationWildcard getSuperFunction(String name) {
        return getSuperFunction(name, null);
    }

    public SuperFunctionInvokationWildcard getSuperFunction(String name, List<Expression> args) {
        SuperFunctionInvokationWildcard res = superFunctionMap.get(name);
        if (res != null) return res;

        res = new SuperFunctionInvokationWildcard(args);
        superFunctionMap.put(name, res);
        return res;
    }

    public MemberFunctionInvokationWildcard getMemberFunction(String name) {
        return memberFunctionMap.get(name);
    }

    public MemberFunctionInvokationWildcard getMemberFunction(String name, String methodname, Expression object) {
        return getMemberFunction(name, methodname, false, object, ListFactory.<Expression>newList());
    }

    public MemberFunctionInvokationWildcard getMemberFunction(String name, String methodname, Expression object, Expression... args) {
        return getMemberFunction(name, methodname, false, object, ListFactory.newImmutableList(args));
    }

    /* When matching a function invokation, we don't really have all the details to construct a plausible
     * MemberFunctionInvokation expression, so just construct something which will match it!
     */
    public MemberFunctionInvokationWildcard getMemberFunction(String name, String methodname, boolean isInitMethod, Expression object, List<Expression> args) {
        MemberFunctionInvokationWildcard res = memberFunctionMap.get(name);
        if (res != null) return res;

        res = new MemberFunctionInvokationWildcard(methodname, isInitMethod, object, args);
        memberFunctionMap.put(name, res);
        return res;
    }

    public StaticFunctionInvokationWildcard getStaticFunction(String name, JavaTypeInstance clazz, JavaTypeInstance returnType, String methodname) {
        return getStaticFunction(name, clazz, returnType, methodname, ListFactory.<Expression>newList());
    }

    public StaticFunctionInvokationWildcard getStaticFunction(String name, JavaTypeInstance clazz, JavaTypeInstance returnType, String methodname, Expression... args) {
        return getStaticFunction(name, clazz, returnType, methodname, ListFactory.newImmutableList(args));
    }

    /* When matching a function invokation, we don't really have all the details to construct a plausible
     * StaticFunctionInvokation expression, so just construct something which will match it!
     */
    public StaticFunctionInvokationWildcard getStaticFunction(String name, JavaTypeInstance clazz, JavaTypeInstance returnType, String methodname, List<Expression> args) {
        StaticFunctionInvokationWildcard res = staticFunctionMap.get(name);
        if (res != null) return res;

        res = new StaticFunctionInvokationWildcard(methodname, clazz, returnType, args);
        staticFunctionMap.put(name, res);
        return res;
    }

    public StaticFunctionInvokationWildcard getStaticFunction(String name) {
        return staticFunctionMap.get(name);
    }

    public StaticVariableWildcard getStaticVariable(String name) {
        return staticVariableWildcardMap.get(name);
    }

    public StaticVariableWildcard getStaticVariable(String name, JavaTypeInstance clazz, InferredJavaType varType) {
        return getStaticVariable(name, clazz, varType, true);
    }

    public StaticVariableWildcard getStaticVariable(String name, JavaTypeInstance clazz, InferredJavaType varType, boolean requireTypeMatch) {
        StaticVariableWildcard res = staticVariableWildcardMap.get(name);
        if (res != null) return res;

        res = new StaticVariableWildcard(varType, clazz, requireTypeMatch);
        staticVariableWildcardMap.put(name, res);
        return res;
    }

    public BlockIdentifierWildcard getBlockIdentifier(String name) {
        BlockIdentifierWildcard res = blockIdentifierWildcardMap.get(name);
        if (res != null) return res;

        res = new BlockIdentifierWildcard();
        blockIdentifierWildcardMap.put(name, res);
        return res;
    }

    // ListWildcard is a little dodgy - relies on erasure!
    public ListWildcard getList(String name) {
        ListWildcard res = listMap.get(name);
        if (res != null) return res;

        res = new ListWildcard();
        listMap.put(name, res);
        return res;
    }

    public boolean match(Object pattern, Object test) {
        return pattern.equals(test);
    }

    private static class DebugDumpable implements Dumpable {
        @Override
        public Dumper dump(Dumper dumper) {
            return dumper.print("" + getClass() + " : " + toString());
        }
    }

    public class LValueWildcard extends DebugDumpable implements LValue, Wildcard<LValue> {
        private final Predicate<LValue> test;
        private transient LValue matchedValue;


        private LValueWildcard(Predicate<LValue> test) {
            this.test = test;
        }

        @Override
        public void markFinal() {

        }

        @Override
        public boolean isFinal() {
            return false;
        }

        @Override
        public void markVar() {

        }

        @Override
        public boolean isVar() {
            return false;
        }

        @Override
        public void collectTypeUsages(TypeUsageCollector collector) {
        }

        @Override
        public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        }

        @Override
        public JavaAnnotatedTypeInstance getAnnotatedCreationType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean doesBlackListLValueReplacement(LValue replace, Expression with) {
            return false;
        }

        @Override
        public LValue deepClone(CloneHelper cloneHelper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LValue outerDeepClone(CloneHelper cloneHelper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNumberOfCreators() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue, ?> ssaIdentifierFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return this;
        }

        @Override
        public InferredJavaType getInferredJavaType() {
            return InferredJavaType.IGNORE;
        }

        @Override
        public Precedence getPrecedence() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Dumper dump(Dumper d, boolean defines) {
            return dump(d);
        }

        @Override
        public Dumper dumpWithOuterPrecedence(Dumper d, Precedence outerPrecedence, Troolean isLhs) {
            return d;
        }

        @Override
        public boolean canThrow(ExceptionCheck caught) {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LValue)) {
                return false;
            }
            if (matchedValue == null) {
                if (test == null || test.test((LValue) o)) {
                    matchedValue = (LValue) o;
                    return true;
                }
                return false;
            }
            return matchedValue.equals(o);
        }

        @Override
        public LValue getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }
    }

    public class StackLabelWildCard extends StackSSALabel implements Wildcard<StackSSALabel> {
        private transient StackSSALabel matchedValue;

        StackLabelWildCard() {
            super(new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.TEST));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StackSSALabel)) {
                return false;
            }
            if (matchedValue == null) {
                matchedValue = (StackSSALabel) o;
                return true;
            }
            return matchedValue.equals(o);
        }

        @Override
        public StackSSALabel getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }
    }

    private static abstract class AbstractBaseExpressionWildcard extends DebugDumpable implements Expression {

        @Override
        public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public boolean isSimple() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValidStatement() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canPushDownInto() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression pushDown(Expression toPush, Expression parent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InferredJavaType getInferredJavaType() {
            return InferredJavaType.IGNORE;
        }

        @Override
        public Expression deepClone(CloneHelper cloneHelper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression outerDeepClone(CloneHelper cloneHelper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Precedence getPrecedence() {
            return Precedence.WEAKEST;
        }

        @Override
        public Dumper dumpWithOuterPrecedence(Dumper d, Precedence outerPrecedence, Troolean isLhs) {
            return dump(d);
        }

        @Override
        public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void collectTypeUsages(TypeUsageCollector collector) {
        }

        @Override
        public boolean canThrow(ExceptionCheck caught) {
            return true;
        }

        @Override
        public Literal getComputedLiteral(Map<LValue, Literal> display) {
            return null;
        }

        @Override
        public <T> T visit(ExpressionVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public class ExpressionWildcard extends AbstractBaseExpressionWildcard implements Wildcard<Expression> {
        private transient Expression matchedValue;

        ExpressionWildcard() {
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Expression)) {
                return false;
            }
            if (matchedValue == null) {
                matchedValue = (Expression) o;
                return true;
            }
            return matchedValue.equals(o);
        }

        @Override
        public Expression getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }
    }

    public static class AnyOneOfExpression extends AbstractBaseExpressionWildcard implements Wildcard<Expression> {

        private Set<Expression> possibles;

        public AnyOneOfExpression(Set<Expression> possibles) {
            this.possibles = possibles;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Expression)) {
                return false;
            }
            return possibles.contains(o);
        }

        @Override
        public Expression getMatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetMatch() {
            throw new UnsupportedOperationException();
        }
    }


    public class NewArrayWildcard extends AbstractBaseExpressionWildcard implements Wildcard<AbstractNewArray> {
        private final int numSizedDims;
        private final Integer numTotalDims;
        private transient AbstractNewArray matchedValue;

        NewArrayWildcard(int numSizedDims, Integer numTotalDims) {
            this.numSizedDims = numSizedDims;
            this.numTotalDims = numTotalDims;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AbstractNewArray)) {
                return false;
            }
            if (matchedValue == null) {
                AbstractNewArray abstractNewArray = (AbstractNewArray) o;
                if (numSizedDims != abstractNewArray.getNumSizedDims()) return false;
                if (numTotalDims != null && numTotalDims != abstractNewArray.getNumDims()) return false;
                matchedValue = abstractNewArray;
                return true;
            }
            return matchedValue.equals(o);
        }

        @Override
        public AbstractNewArray getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

    }

    public class MemberFunctionInvokationWildcard extends AbstractBaseExpressionWildcard implements Wildcard<MemberFunctionInvokation> {
        private final String name;
        private final boolean isInitMethod;
        private final Expression object;
        private final List<Expression> args;
        private transient MemberFunctionInvokation matchedValue;

        MemberFunctionInvokationWildcard(String name, boolean isInitMethod, Expression object, List<Expression> args) {
            this.name = name;
            this.isInitMethod = isInitMethod;
            this.object = object;
            this.args = args;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MemberFunctionInvokation)) return false;
            if (matchedValue != null) return matchedValue.equals(o);

            /*
             * See if this is a compatible member function.
             *
             * TODO : since it might fail, we need to rewind any captures!
             */
            MemberFunctionInvokation other = (MemberFunctionInvokation) o;
            if (isInitMethod != other.isInitMethod()) return false;
            // always match null name.
            if (name != null && !name.equals(other.getName())) {
                return false;
            }
            if (!object.equals(other.getObject())) return false;
            List<Expression> otherArgs = other.getArgs();
            if (args != null) {
                if (args.size() != otherArgs.size()) return false;
                for (int x = 0; x < args.size(); ++x) {
                    Expression myArg = args.get(x);
                    Expression hisArg = otherArgs.get(x);
                    if (!myArg.equals(hisArg)) return false;
                }
            }
            matchedValue = (MemberFunctionInvokation) o;
            return true;
        }

        @Override
        public MemberFunctionInvokation getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

    }

    public class SuperFunctionInvokationWildcard extends AbstractBaseExpressionWildcard implements Wildcard<SuperFunctionInvokation> {
        private final List<Expression> args;
        private transient SuperFunctionInvokation matchedValue;

        SuperFunctionInvokationWildcard(List<Expression> args) {
            this.args = args;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SuperFunctionInvokation)) return false;
            if (matchedValue != null) return matchedValue.equals(o);

            /*
             * See if this is a compatible member function.
             *
             * TODO : since it might fail, we need to rewind any captures!
             */
            SuperFunctionInvokation other = (SuperFunctionInvokation) o;
            if (args != null) {
                List<Expression> otherArgs = other.getArgs();
                if (args.size() != otherArgs.size()) return false;
                for (int x = 0; x < args.size(); ++x) {
                    Expression myArg = args.get(x);
                    Expression hisArg = otherArgs.get(x);
                    if (!myArg.equals(hisArg)) return false;
                }
            }
            matchedValue = other;
            return true;
        }

        @Override
        public SuperFunctionInvokation getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

    }


    public class StaticFunctionInvokationWildcard extends AbstractBaseExpressionWildcard implements Wildcard<StaticFunctionInvokation> {
        private final String name;
        private final JavaTypeInstance clazz;
        private final JavaTypeInstance returnType;
        private final List<Expression> args;
        private transient StaticFunctionInvokation matchedValue;

        StaticFunctionInvokationWildcard(String name, JavaTypeInstance clazz, JavaTypeInstance returnType, List<Expression> args) {
            this.name = name;
            this.clazz = clazz;
            this.args = args;
            this.returnType = returnType;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StaticFunctionInvokation)) return false;
            if (matchedValue != null) return matchedValue.equals(o);

            StaticFunctionInvokation other = (StaticFunctionInvokation) o;
            if (name != null) {
                if (!name.equals(other.getName())) return false;
            }
            if (returnType != null) {
                if (!returnType.equals(other.getInferredJavaType().getJavaTypeInstance())) return false;
            }
            if (!clazz.equals(other.getClazz())) return false;
            List<Expression> otherArgs = other.getArgs();
            if (args != null) {
                if (args.size() != otherArgs.size()) return false;
                for (int x = 0; x < args.size(); ++x) {
                    Expression myArg = args.get(x);
                    Expression hisArg = otherArgs.get(x);
                    if (!myArg.equals(hisArg)) return false;
                }
            }
            matchedValue = other;
            return true;
        }

        @Override
        public StaticFunctionInvokation getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

    }


    public class BlockIdentifierWildcard extends BlockIdentifier implements Wildcard<BlockIdentifier> {
        private BlockIdentifier matchedValue;

        BlockIdentifierWildcard() {
            super(0, null);
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;

            if (matchedValue != null) return matchedValue.equals(o);

            if (!(o instanceof BlockIdentifier)) return false;

            matchedValue = (BlockIdentifier) o;
            return true;
        }

        @Override
        public BlockIdentifier getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

    }

    public class ListWildcard extends AbstractList implements Wildcard<List> {
        private List matchedValue;


        @Override
        public Object get(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;

            if (matchedValue != null) return matchedValue.equals(o);

            if (!(o instanceof List)) return false;

            List other = (List) o;
            matchedValue = other;
            return true;
        }

        @Override
        public List getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

    }

    public class StaticVariableWildcard extends StaticVariable implements Wildcard<StaticVariable> {
        private StaticVariable matchedValue;
        private final boolean requireTypeMatch;

        StaticVariableWildcard(InferredJavaType type, JavaTypeInstance clazz, boolean requireTypeMatch) {
            super(type, clazz, null);
            this.requireTypeMatch = requireTypeMatch;
        }

        @Override
        public StaticVariable getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;

            if (matchedValue != null) return matchedValue.equals(o);

            if (!(o instanceof StaticVariable)) return false;
            StaticVariable other = (StaticVariable) o;

            if (!this.getOwningClassType().equals(other.getOwningClassType())) return false;
            JavaTypeInstance thisType = this.getInferredJavaType().getJavaTypeInstance();
            JavaTypeInstance otherType = other.getInferredJavaType().getJavaTypeInstance();
            if (requireTypeMatch && !thisType.equals(otherType)) {
                return false;
            }
            matchedValue = other;
            return true;
        }
    }

    public class ConstructorInvokationSimpleWildcard extends AbstractBaseExpressionWildcard implements Wildcard<ConstructorInvokationSimple> {
        private ConstructorInvokationSimple matchedValue;

        private final JavaTypeInstance clazz;
        private final List<Expression> args;

        ConstructorInvokationSimpleWildcard(JavaTypeInstance clazz, List<Expression> args) {
            this.clazz = clazz;
            this.args = args;
        }

        @Override
        public ConstructorInvokationSimple getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (!(o instanceof ConstructorInvokationSimple)) return false;

            if (matchedValue != null) {
                return matchedValue.equals(o);
            }

            ConstructorInvokationSimple other = (ConstructorInvokationSimple) o;
            if (!clazz.equals(other.getTypeInstance())) return false;
            if (args != null && args.equals(other.getArgs())) return false;

            matchedValue = other;
            return true;
        }
    }

    public class ConstructorInvokationAnonymousInnerWildcard extends AbstractBaseExpressionWildcard implements Wildcard<ConstructorInvokationAnonymousInner> {
        private ConstructorInvokationAnonymousInner matchedValue;

        private final JavaTypeInstance clazz;
        private final List<Expression> args;

        ConstructorInvokationAnonymousInnerWildcard(JavaTypeInstance clazz, List<Expression> args) {
            this.clazz = clazz;
            this.args = args;
        }

        @Override
        public ConstructorInvokationAnonymousInner getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (!(o instanceof ConstructorInvokationAnonymousInner)) return false;

            if (matchedValue != null) {
                return matchedValue.equals(o);
            }

            ConstructorInvokationAnonymousInner other = (ConstructorInvokationAnonymousInner) o;
            JavaTypeInstance otherType = other.getTypeInstance();
            if (clazz != null && !clazz.equals(otherType)) return false;
            if (args != null && args.equals(other.getArgs())) return false;

            matchedValue = other;
            return true;
        }
    }

    public class ArithmeticMutationWildcard extends AbstractBaseExpressionWildcard implements Wildcard<ArithmeticMutationOperation> {
        private final OptionalMatch<LValue> lhs;
        private final OptionalMatch<Expression> rhs;
        private final OptionalMatch<ArithOp> op;

        ArithmeticMutationWildcard(Optional<LValue> lhs, Optional<Expression> rhs, Optional<ArithOp> op) {
            this.lhs = new OptionalMatch<LValue>(lhs);
            this.rhs = new OptionalMatch<Expression>(rhs);
            this.op = new OptionalMatch<ArithOp>(op);
        }

        @Override
        public ArithmeticMutationOperation getMatch() {
            return null;
        }

        public OptionalMatch<ArithOp> getOp() {
            return op;
        }

        @Override
        public void resetMatch() {
            lhs.reset();
            rhs.reset();
            op.reset();
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (!(o instanceof ArithmeticMutationOperation)) return false;
            ArithmeticMutationOperation other = (ArithmeticMutationOperation)o;

            if (!lhs.match(other.getUpdatedLValue())) return false;
            if (!rhs.match(other.getMutation())) return false;
            if (!op.match(other.getOp())) return false;
            return true;
        }
    }



    public class CastExpressionWildcard extends AbstractBaseExpressionWildcard implements Wildcard<CastExpression> {
        private final JavaTypeInstance clazz;
        private CastExpression matchedValue;

        private Expression expression;

        CastExpressionWildcard(JavaTypeInstance clazz, Expression expression) {
            this.clazz = clazz;
            this.expression = expression;
        }

        @Override
        public CastExpression getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (!(o instanceof CastExpression)) return false;

            if (matchedValue != null) {
                return matchedValue.equals(o);
            }

            CastExpression other = (CastExpression) o;
            if (clazz != null && !clazz.equals(other.getInferredJavaType().getJavaTypeInstance())) return false;
            if (!expression.equals(other.getChild())) return false;


            matchedValue = other;
            return true;
        }
    }


    public class ConditionalExpressionWildcard extends AbstractBaseExpressionWildcard implements ConditionalExpression, Wildcard<ConditionalExpression> {
        private ConditionalExpression matchedValue;

        ConditionalExpressionWildcard() {
        }

        @Override
        public ConditionalExpression getMatch() {
            return matchedValue;
        }

        @Override
        public void resetMatch() {
            matchedValue = null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (!(o instanceof ConditionalExpression)) return false;

            if (matchedValue != null) {
                return matchedValue.equals(o);
            }

            matchedValue = (ConditionalExpression) o;
            return true;
        }

        @Override
        public ConditionalExpression simplify() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConditionalExpression optimiseForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<LValue> getLoopLValues() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConditionalExpression getDemorganApplied(boolean amNegating) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConditionalExpression getRightDeep() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSize(Precedence outerPrecedence) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConditionalExpression getNegated() {
            throw new UnsupportedOperationException();
        }
    }

    public class BlockWildcard extends Block implements Wildcard<Block> {
        private Block match;

        BlockWildcard() {
            super(null, false);
        }

        @Override
        public Block getMatch() {
            return match;
        }

        @Override
        public void resetMatch() {
            match = null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (!(o instanceof Block)) return false;

            if (match != null) {
                // NB: THIS IS A VERY DELIBERATE == MATCH.
                return match == o;
            }

            match = (Block) o;
            return true;
        }
    }

}

