package org.benf.cfr.reader.bytecode.analysis.parse.utils.scope;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public abstract class AbstractLValueScopeDiscoverer implements LValueScopeDiscoverer {

    /*
     * We keep track of the first definition for a given variable.  If we exit the scope that the variable
     * is defined at (i.e. scope depth goes above) we have to remove all earliest definitions at that level.
     */
    final Map<NamedVariable, ScopeDefinition> earliestDefinition = MapFactory.newIdentityMap();
    final Map<Integer, Map<NamedVariable, Boolean>> earliestDefinitionsByLevel = MapFactory.newLazyMap(new UnaryFunction<Integer, Map<NamedVariable, Boolean>>() {
        @Override
        public Map<NamedVariable, Boolean> invoke(Integer arg) {
            return MapFactory.newIdentityMap();
        }
    });
    transient int currentDepth = 0;

    transient Stack<StatementContainer<StructuredStatement>> currentBlock = new Stack<StatementContainer<StructuredStatement>>();

    final List<ScopeDefinition> discoveredCreations = ListFactory.newList();
    final VariableFactory variableFactory;
    StatementContainer<StructuredStatement> currentMark = null;

    AbstractLValueScopeDiscoverer(MethodPrototype prototype, VariableFactory variableFactory) {
        final List<LocalVariable> parameters = prototype.getComputedParameters();
        this.variableFactory = variableFactory;
        for (LocalVariable parameter : parameters) {
            InferredJavaType inferredJavaType = parameter.getInferredJavaType();
            final ScopeDefinition prototypeScope = new ScopeDefinition(0, null, null, parameter, inferredJavaType, parameter.getName());
            earliestDefinition.put(parameter.getName(), prototypeScope);
        }
    }

    @Override
    public void enterBlock(StructuredStatement structuredStatement) {
        StatementContainer<StructuredStatement> container = structuredStatement.getContainer();
        if (container == null) {
            return;
        }
        currentBlock.push(container);
        currentDepth++;
    }

    @Override
    public void processOp04Statement(Op04StructuredStatement statement) {
        statement.getStatement().traceLocalVariableScope(this);
    }

    @Override
    public void mark(StatementContainer<StructuredStatement> mark) {
        currentMark = mark;
    }

    @Override
    public void leaveBlock(StructuredStatement structuredStatement) {
        Op04StructuredStatement container = structuredStatement.getContainer();
        if (container == null) {
            return;
        }
        for (NamedVariable definedHere : earliestDefinitionsByLevel.get(currentDepth).keySet()) {
            earliestDefinition.remove(definedHere);
        }
        earliestDefinitionsByLevel.remove(currentDepth);
        StatementContainer<StructuredStatement> oldContainer = currentBlock.pop();
        if (container != oldContainer) {
            throw new IllegalStateException();
        }
        currentDepth--;
    }

    @Override
    public void collect(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }

    @Override
    public void collectMultiUse(StackSSALabel lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }

    @Override
    public void collectMutatedLValue(LValue lValue, StatementContainer<StructuredStatement> statementContainer, Expression value) {

    }

    private static class ScopeKey {
        private final LValue lValue;
        private final JavaTypeInstance type;

        private ScopeKey(LValue lValue, JavaTypeInstance type) {
            this.lValue = lValue;
//            this.type = type.getDeGenerifiedType();
            // Using the degenerified type causes us to 'correctly' combine a variable where it's been split into generic
            // and non-generic types, but I can't convince myself it doesn't have scope for illegal combining.
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ScopeKey scopeKey = (ScopeKey) o;

            if (!lValue.equals(scopeKey.lValue)) return false;
            if (!type.equals(scopeKey.type)) return false;

            return true;
        }

        private LValue getlValue() {
            return lValue;
        }

        @Override
        public int hashCode() {
            int result = lValue.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }
    }

    public void markDiscoveredCreations() {
        /*
         * Eliminate enclosing scopes where they were falsely detected, and
         * where scopes for the same variable exist, lift to the lowest common denominator.
         */
        Map<ScopeKey, List<ScopeDefinition>> definitionsByType = Functional.groupToMapBy(discoveredCreations, new UnaryFunction<ScopeDefinition, ScopeKey>() {
            @Override
            public ScopeKey invoke(ScopeDefinition arg) {
                return arg.getScopeKey();
            }
        });

        for (Map.Entry<ScopeKey, List<ScopeDefinition>> entry : definitionsByType.entrySet()) {
            ScopeKey scopeKey = entry.getKey();
            List<ScopeDefinition> definitions = entry.getValue();
            // find the longest common nested scope - null wins automatically!

            List<StatementContainer<StructuredStatement>> commonScope = null;
            ScopeDefinition bestDefn = null;
            LValue scopedEntity = scopeKey.getlValue();
            for (ScopeDefinition definition : definitions) {
                StructuredStatement statement = definition.getStatementContainer().getStatement();

                if (statement.alwaysDefines(scopedEntity)) {
                    statement.markCreator(scopedEntity, null);
                    continue;
                }
                List<StatementContainer<StructuredStatement>> scopeList = definition.getNestedScope();
                if (scopeList.isEmpty()) scopeList = null;

                if (scopeList == null) {
                    commonScope = null;
                    bestDefn = definition;
                    break;
                }

                if (commonScope == null) {
                    commonScope = scopeList;
                    bestDefn = definition;
                    continue;
                }
                // Otherwise, take the common prefix.
                commonScope = getCommonPrefix(commonScope, scopeList);
                if (commonScope.size() == scopeList.size()) {
                    bestDefn = definition;
                } else {
                    bestDefn = null;
                }
            }
            // But - we can only accept the first definition as a 'location'.
            // This is because we might be combining two declarations, in which case we
            // will NOT want to use the later one!
            if (bestDefn != definitions.get(0)) bestDefn = null;
            StatementContainer<StructuredStatement> creationContainer = null;
            if (scopedEntity instanceof SentinelLocalClassLValue) {
                List<StatementContainer<StructuredStatement>> scope = null;
                if (bestDefn != null) {
                    scope = bestDefn.getNestedScope();
                } else if (commonScope != null) {
                    scope = commonScope;
                }

                if (scope != null) {
                    for (int i = scope.size() - 1; i >= 0; --i) {
                        StatementContainer<StructuredStatement> thisItem = scope.get(i);
                        if (thisItem.getStatement() instanceof Block) {
                            Block block = (Block) thisItem.getStatement();
                            block.setIndenting(true);
                            creationContainer = thisItem;
                            break;
                        }
                    }
                }
            } else {
                if (bestDefn != null) {
                    creationContainer = bestDefn.getStatementContainer();
                } else if (commonScope != null) {
                    creationContainer = commonScope.get(commonScope.size() - 1);
                }
            }

            StatementContainer<StructuredStatement> hint = bestDefn == null ? null : bestDefn.localHint;
            if (creationContainer != null) {
                // Could make use of the fact that if something here is void, we've obviously screwed up.
//                if (scopedEntity.getInferredJavaType().getJavaTypeInstance() == RawJavaType.VOID) {
//                    throw new ConfusedCFRException("Void");
//                }
                creationContainer.getStatement().markCreator(scopedEntity, hint);
            }
        }
    }

    private static <T> List<T> getCommonPrefix(List<T> a, List<T> b) {
        List<T> la, lb;
        if (a.size() < b.size()) {
            la = a;
            lb = b;
        } else {
            la = b;
            lb = a;
        }
        // la is shortest or equal.
        int maxRes = Math.min(la.size(), lb.size());
        int sameLen = 0;
        for (int x = 0; x < maxRes; ++x, ++sameLen) {
            if (!la.get(x).equals(lb.get(x))) break;
        }
        if (sameLen == la.size()) return la;
        return la.subList(0, sameLen);
    }

    static class ScopeDefinition {
        private final int depth;
        // Keeping this nested scope is woefully inefficient.... fixme.
        private final List<StatementContainer<StructuredStatement>> nestedScope;
        private final StatementContainer<StructuredStatement> exactStatement;
        private final StatementContainer<StructuredStatement> localHint;
        private final LValue lValue;
        private final JavaTypeInstance lValueType;
        private final NamedVariable name;
        private final ScopeKey scopeKey;

        ScopeDefinition(int depth, Stack<StatementContainer<StructuredStatement>> nestedScope, StatementContainer<StructuredStatement> exactStatement,
                        LValue lValue, InferredJavaType inferredJavaType, NamedVariable name) {
            this(depth, nestedScope, exactStatement, lValue, getUnclashedType(inferredJavaType), name, null);
        }

        private static JavaTypeInstance getUnclashedType(InferredJavaType inferredJavaType) {
            if (inferredJavaType.isClash()) {
                inferredJavaType.collapseTypeClash();
            }
            return inferredJavaType.getJavaTypeInstance();
        }

        ScopeDefinition(int depth, Stack<StatementContainer<StructuredStatement>> nestedScope, StatementContainer<StructuredStatement> exactStatement,
                        LValue lValue, JavaTypeInstance type, NamedVariable name, StatementContainer<StructuredStatement> hint) {
            this.depth = depth;
            Pair< List<StatementContainer<StructuredStatement>>, StatementContainer<StructuredStatement>> adjustedScope = getBestScopeFor(lValue, nestedScope, exactStatement);
            this.nestedScope = adjustedScope.getFirst();
            this.exactStatement = adjustedScope.getSecond();
            this.lValue = lValue;
            this.lValueType = type;
            this.name = name;
            this.localHint = hint;
            this.scopeKey = new ScopeKey(lValue, type);
        }

        // nestedScope == null ? null : ListFactory.newList(nestedScope);
        private static Pair< List<StatementContainer<StructuredStatement>>, StatementContainer<StructuredStatement>> getBestScopeFor(
                                     LValue lValue,
                                     Collection<StatementContainer<StructuredStatement>> nestedScope,
                                     StatementContainer<StructuredStatement> exactStatement) {
            if (nestedScope == null) return Pair.make(null, exactStatement);
            List<StatementContainer<StructuredStatement>> scope = ListFactory.newList(nestedScope);
            if (exactStatement != null && exactStatement.getStatement().alwaysDefines(lValue)) return Pair.make(scope, exactStatement);
            if (scope.isEmpty()) return Pair.make(scope, exactStatement);
            for (int x=scope.size()-1;x>=0;--x) {
                StatementContainer<StructuredStatement> scopeTest = scope.get(x);
                if (scopeTest.getStatement().canDefine(lValue)) break;
                scope.remove(x);
            }
            if (scope.size() == nestedScope.size()) return Pair.make(scope, exactStatement);
            if (scope.isEmpty()) return Pair.make(null, exactStatement);
            exactStatement = scope.get(scope.size()-1);

            return Pair.make(scope, exactStatement);
        }

        public JavaTypeInstance getJavaTypeInstance() {
            return lValueType;
        }

        public StatementContainer<StructuredStatement> getStatementContainer() {
            return exactStatement;
        }

        public LValue getlValue() {
            return lValue;
        }

        int getDepth() {
            return depth;
        }

        public NamedVariable getName() {
            return name;
        }

        ScopeKey getScopeKey() {
            return scopeKey;
        }

        List<StatementContainer<StructuredStatement>> getNestedScope() {
            return nestedScope;
        }

        @Override
        public String toString() {
            return name + " : " + lValueType.getRawName();
        }
    }

}
