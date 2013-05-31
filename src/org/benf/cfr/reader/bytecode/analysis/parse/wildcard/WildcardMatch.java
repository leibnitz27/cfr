package org.benf.cfr.reader.bytecode.analysis.parse.wildcard;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/07/2012
 * Time: 17:22
 */
public class WildcardMatch {

    /*
     * This could probably be done with one map....
     */
    private Map<String, LValueWildcard> lValueMap = MapFactory.newMap();
    private Map<String, ExpressionWildcard> expressionMap = MapFactory.newMap();
    private Map<String, NewArrayWildcard> newArrayWildcardMap = MapFactory.newMap();
    private Map<String, MemberFunctionInvokationWildcard> memberFunctionMap = MapFactory.newMap();
    private Map<String, SuperFunctionInvokationWildcard> superFunctionMap = MapFactory.newMap();
    private Map<String, StaticFunctionInvokationWildcard> staticFunctionMap = MapFactory.newMap();
    private Map<String, BlockIdentifierWildcard> blockIdentifierWildcardMap = MapFactory.newMap();
    private Map<String, ListWildcard> listMap = MapFactory.newMap();
    private Map<String, StaticVariableWildcard> staticVariableWildcardMap = MapFactory.newMap();
    private Map<String, ConstructorInvokationSimpleWildcard> constructorWildcardMap = MapFactory.newMap();
    private Map<String, CastExpressionWildcard> castWildcardMap = MapFactory.newMap();
    private Map<String, ConditionalExpressionWildcard> conditionalWildcardMap = MapFactory.newMap();

    private <T> void reset(Collection<? extends Wildcard<T>> coll) {
        for (Wildcard<T> item : coll) {
            item.resetMatch();
        }
    }

    public void reset() {
        reset(lValueMap.values());
        reset(expressionMap.values());
        reset(newArrayWildcardMap.values());
        reset(memberFunctionMap.values());
        reset(blockIdentifierWildcardMap.values());
        reset(listMap.values());
        reset(staticFunctionMap.values());
        reset(staticVariableWildcardMap.values());
        reset(superFunctionMap.values());
        reset(constructorWildcardMap.values());
        reset(castWildcardMap.values());
        reset(conditionalWildcardMap.values());
    }

    public ConditionalExpressionWildcard getConditionalExpressionWildcard(String name) {
        ConditionalExpressionWildcard res = conditionalWildcardMap.get(name);
        if (res != null) return res;

        res = new ConditionalExpressionWildcard();
        conditionalWildcardMap.put(name, res);
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

    public LValueWildcard getLValueWildCard(String name) {
        LValueWildcard res = lValueMap.get(name);
        if (res != null) return res;

        res = new LValueWildcard(name);
        lValueMap.put(name, res);
        return res;
    }

    public ExpressionWildcard getExpressionWildCard(String name) {
        ExpressionWildcard res = expressionMap.get(name);
        if (res != null) return res;

        res = new ExpressionWildcard(name);
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

    public CastExpressionWildcard getCastExpressionWildcard(String name) {
        return castWildcardMap.get(name);
    }

    public NewArrayWildcard getNewArrayWildCard(String name) {
        return getNewArrayWildCard(name, 1, null);
    }

    public NewArrayWildcard getNewArrayWildCard(String name, int numSizedDims, Integer numTotalDims) {
        NewArrayWildcard res = newArrayWildcardMap.get(name);
        if (res != null) return res;

        res = new NewArrayWildcard(name, numSizedDims, numTotalDims);
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
        return getMemberFunction(name, methodname, object, ListFactory.<Expression>newList());
    }

    public MemberFunctionInvokationWildcard getMemberFunction(String name, String methodname, Expression object, Expression... args) {
        return getMemberFunction(name, methodname, object, ListFactory.<Expression>newList(args));
    }

    /* When matching a function invokation, we don't really have all the details to construct a plausible
     * MemberFunctionInvokation expression, so just construct something which will match it!
     */
    public MemberFunctionInvokationWildcard getMemberFunction(String name, String methodname, Expression object, List<Expression> args) {
        MemberFunctionInvokationWildcard res = memberFunctionMap.get(name);
        if (res != null) return res;

        res = new MemberFunctionInvokationWildcard(methodname, object, args);
        memberFunctionMap.put(name, res);
        return res;
    }

    public StaticFunctionInvokationWildcard getStaticFunction(String name, JavaTypeInstance clazz, String methodname) {
        return getStaticFunction(name, clazz, methodname, ListFactory.<Expression>newList());
    }

    public StaticFunctionInvokationWildcard getStaticFunction(String name, JavaTypeInstance clazz, String methodname, Expression... args) {
        return getStaticFunction(name, clazz, methodname, ListFactory.<Expression>newList(args));
    }

    /* When matching a function invokation, we don't really have all the details to construct a plausible
     * StaticFunctionInvokation expression, so just construct something which will match it!
     */
    public StaticFunctionInvokationWildcard getStaticFunction(String name, JavaTypeInstance clazz, String methodname, List<Expression> args) {
        StaticFunctionInvokationWildcard res = staticFunctionMap.get(name);
        if (res != null) return res;

        res = new StaticFunctionInvokationWildcard(methodname, clazz, args);
        staticFunctionMap.put(name, res);
        return res;
    }

    public StaticVariableWildcard getStaticVariable(String name) {
        return staticVariableWildcardMap.get(name);
    }

    public StaticVariableWildcard getStaticVariable(String name, JavaTypeInstance clazz, InferredJavaType varType) {
        StaticVariableWildcard res = staticVariableWildcardMap.get(name);
        if (res != null) return res;

        res = new StaticVariableWildcard(varType, clazz);
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

    public <T> ListWildcard getList(String name) {
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
        private final String name;
        private transient LValue matchedValue;

        private LValueWildcard(String name) {
            this.name = name;
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
        public SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory) {
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
        public boolean equals(Object o) {
            if (!(o instanceof LValue)) {
                return false;
            }
            if (matchedValue == null) {
                matchedValue = (LValue) o;
                return true;
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

    private abstract class AbstractBaseExpressionWildcard extends DebugDumpable implements Expression {

        @Override
        public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSimple() {
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
        public Dumper dumpWithOuterPrecedence(Dumper d, int outerPrecedence) {
            return dump(d);
        }
    }

    public class ExpressionWildcard extends AbstractBaseExpressionWildcard implements Wildcard<Expression> {
        private final String name;
        private transient Expression matchedValue;

        public ExpressionWildcard(String name) {
            this.name = name;
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

    public class NewArrayWildcard extends AbstractBaseExpressionWildcard implements Wildcard<AbstractNewArray> {
        private final String name;
        private final int numSizedDims;
        private final Integer numTotalDims;
        private transient AbstractNewArray matchedValue;

        public NewArrayWildcard(String name, int numSizedDims, Integer numTotalDims) {
            this.name = name;
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
        private final Expression object;
        private final List<Expression> args;
        private transient MemberFunctionInvokation matchedValue;

        public MemberFunctionInvokationWildcard(String name, Expression object, List<Expression> args) {
            this.name = name;
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
            if (name == null) {
                if (other.getName() != null) return false;
            } else {
                if (!name.equals(other.getName())) return false;
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

        public SuperFunctionInvokationWildcard(List<Expression> args) {
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


    public class StaticFunctionInvokationWildcard extends AbstractBaseExpressionWildcard implements Wildcard<Expression> {
        private final String name;
        private final JavaTypeInstance clazz;
        private final List<Expression> args;
        private transient Expression matchedValue;

        public StaticFunctionInvokationWildcard(String name, JavaTypeInstance clazz, List<Expression> args) {
            this.name = name;
            this.clazz = clazz;
            this.args = args;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StaticFunctionInvokation)) return false;
            if (matchedValue != null) return matchedValue.equals(o);

            /*
             * See if this is a compatible member function.
             *
             * TODO : since it might fail, we need to rewind any captures!
             */
            StaticFunctionInvokation other = (StaticFunctionInvokation) o;
            if (!name.equals(other.getName())) return false;
            if (!clazz.equals(other.getClazz())) return false;
            List<Expression> otherArgs = other.getArgs();
            if (args.size() != otherArgs.size()) return false;
            for (int x = 0; x < args.size(); ++x) {
                Expression myArg = args.get(x);
                Expression hisArg = otherArgs.get(x);
                if (!myArg.equals(hisArg)) return false;
            }
            matchedValue = (Expression) o;
            return true;
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


    public class BlockIdentifierWildcard extends BlockIdentifier implements Wildcard<BlockIdentifier> {
        private BlockIdentifier matchedValue;

        public BlockIdentifierWildcard() {
            super(0, null);
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;

            if (matchedValue != null) return matchedValue.equals(o);

            if (!(o instanceof BlockIdentifier)) return false;

            BlockIdentifier other = (BlockIdentifier) o;
            matchedValue = other;
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

        public StaticVariableWildcard(InferredJavaType type, JavaTypeInstance clazz) {
            super(type, clazz, null);
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

            if (!this.getOwningClassTypeInstance().equals(other.getOwningClassTypeInstance())) return false;
            if (!this.getInferredJavaType().getJavaTypeInstance().equals(other.getInferredJavaType().getJavaTypeInstance()))
                return false;
            matchedValue = other;
            return true;
        }
    }

    public class ConstructorInvokationSimpleWildcard extends AbstractBaseExpressionWildcard implements Wildcard<ConstructorInvokationSimple> {
        private ConstructorInvokationSimple matchedValue;

        private final JavaTypeInstance clazz;
        private final List<Expression> args;

        public ConstructorInvokationSimpleWildcard(JavaTypeInstance clazz, List<Expression> args) {
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


    public class CastExpressionWildcard extends AbstractBaseExpressionWildcard implements Wildcard<CastExpression> {
        private final JavaTypeInstance clazz;
        private CastExpression matchedValue;

        private Expression expression;

        public CastExpressionWildcard(JavaTypeInstance clazz, Expression expression) {
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

        public ConditionalExpressionWildcard() {
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

            ConditionalExpression other = (ConditionalExpression) o;

            matchedValue = other;
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
        public int getSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConditionalExpression getNegated() {
            throw new UnsupportedOperationException();
        }


    }

}

