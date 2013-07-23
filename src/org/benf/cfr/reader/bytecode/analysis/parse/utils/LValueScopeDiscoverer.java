package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/03/2013
 * Time: 06:09
 */
public class LValueScopeDiscoverer implements LValueAssignmentCollector<StructuredStatement>, LValueUsageCollector {

    /*
     * We keep track of the first definition for a given variable.  If we exit the scope that the variable
     * is defined at (i.e. scope depth goes above) we have to remove all earliest definitions at that level.
     */
    private final Map<String, ScopeDefinition> earliestDefinition = MapFactory.newMap();
    private final Map<Integer, Set<String>> earliestDefinitionsByLevel = MapFactory.newLazyMap(new UnaryFunction<Integer, Set<String>>() {
        @Override
        public Set<String> invoke(Integer arg) {
            return SetFactory.newSet();
        }
    });
    private transient int currentDepth = 0;
    private transient Stack<StatementContainer<StructuredStatement>> currentBlock = new Stack<StatementContainer<StructuredStatement>>();

    private final List<ScopeDefinition> discoveredCreations = ListFactory.newList();

    public LValueScopeDiscoverer(MethodPrototype prototype, Method.MethodConstructor constructorFlag) {
        final List<LocalVariable> parameters = prototype.getParameters(constructorFlag);
        for (LocalVariable parameter : parameters) {
            final ScopeDefinition prototypeScope = new ScopeDefinition(0, null, null, parameter, parameter.getName());
            earliestDefinition.put(parameter.getName(), prototypeScope);
        }
    }

    public void enterBlock(StructuredStatement structuredStatement) {
        currentBlock.push(structuredStatement.getContainer());
        currentDepth++;
    }

    public void leaveBlock(StructuredStatement structuredStatement) {
        for (String definedHere : earliestDefinitionsByLevel.get(currentDepth)) {
            earliestDefinition.remove(definedHere);
        }
        earliestDefinitionsByLevel.remove(currentDepth);
        StatementContainer<StructuredStatement> oldContainer = currentBlock.pop();
        if (structuredStatement.getContainer() != oldContainer) {
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

    @Override
    public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value) {
        // Note that just because two local variables in the same scope have the same name, they're not NECESSARILY
        // the same variable - if we've reused a stack location, and don't have any naming hints, the name will have
        // been re-used.  This is why we also have to verify that the type of the new assignment is the same as the type
        // of the previous one, and kick out the previous (and remove from earlier scopes) if that's the case).
        String name = localVariable.getName();
        ScopeDefinition previousDef = earliestDefinition.get(name);
        if (previousDef == null) {
            // First use is here.
            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, statementContainer, localVariable, name);
            earliestDefinition.put(name, scopeDefinition);
            earliestDefinitionsByLevel.get(currentDepth).add(name);
            discoveredCreations.add(scopeDefinition);
            return;
        }

        /*
         * Else verify type.
         */
        JavaTypeInstance oldType = previousDef.getJavaTypeInstance();
        JavaTypeInstance newType = localVariable.getInferredJavaType().getJavaTypeInstance();
        if (!oldType.equals(newType)) {
            //
            // TODO : Should rename variable, or we'll have a clash.
            //
            earliestDefinitionsByLevel.get(previousDef.getDepth()).remove(previousDef.getName());
            if (previousDef.getDepth() == currentDepth) {
                throw new UnsupportedOperationException("Re-use of anonymous local variable in same scope with different type.  Renaming required. NYI.");
            }

            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, statementContainer, localVariable, name);
            earliestDefinition.put(name, scopeDefinition);
            earliestDefinitionsByLevel.get(currentDepth).add(name);
            discoveredCreations.add(scopeDefinition);
        }
    }

    private static class ScopeKey {
        private final LocalVariable lValue;
        private final JavaTypeInstance type;

        private ScopeKey(LocalVariable lValue, JavaTypeInstance type) {
            this.lValue = lValue;
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

        List<ScopeDefinition> finalDefinitions = ListFactory.newList();
        for (Map.Entry<ScopeKey, List<ScopeDefinition>> entry : definitionsByType.entrySet()) {
            ScopeKey scopeKey = entry.getKey();
            List<ScopeDefinition> definitions = entry.getValue();
            // find the longest common nested scope - null wins automatically!

            List<StatementContainer<StructuredStatement>> commonScope = null;
            ScopeDefinition bestDefn = null;
            for (ScopeDefinition definition : definitions) {
                List<StatementContainer<StructuredStatement>> scopeList = definition.getNestedScope();
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
            StatementContainer<StructuredStatement> creationContainer;
            if (bestDefn != null) {
                creationContainer = bestDefn.getStatementContainer();
            } else {
                creationContainer = commonScope.get(commonScope.size() - 1);
            }

            creationContainer.getStatement().markCreator(scopeKey.lValue);
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
    /*
     *
     */

    @Override
    public void collect(LValue lValue) {
        if (!(lValue instanceof LocalVariable)) return;
        LocalVariable localVariable = (LocalVariable) lValue;
        String name = localVariable.getName();
        if (name.equals(MiscConstants.THIS)) return;

        ScopeDefinition previousDef = earliestDefinition.get(name);
        // If it's in scope, no problem.
        if (previousDef != null) return;

        // If it's out of scope, we have a variable defined but only assigned in an inner scope, but used in the
        // outer scope later!
        ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, currentBlock.peek(), localVariable, name);
        earliestDefinition.put(name, scopeDefinition);
        earliestDefinitionsByLevel.get(currentDepth).add(name);
        discoveredCreations.add(scopeDefinition);
    }

    /*
     *
     */

    private static class ScopeDefinition {
        private final int depth;
        // Keeping this nested scope is woefully inefficient.... fixme.
        private final List<StatementContainer<StructuredStatement>> nestedScope;
        private final StatementContainer<StructuredStatement> exactStatement;
        private final LocalVariable lValue;
        private final JavaTypeInstance lValueType;
        private final String name;
        private final ScopeKey scopeKey;

        private ScopeDefinition(int depth, Stack<StatementContainer<StructuredStatement>> nestedScope, StatementContainer<StructuredStatement> exactStatement, LocalVariable lValue, String name) {
            this.depth = depth;
            this.nestedScope = nestedScope == null ? null : ListFactory.newList(nestedScope);
            this.exactStatement = exactStatement;
            this.lValue = lValue;
            this.lValueType = lValue.getInferredJavaType().getJavaTypeInstance();
            this.name = name;
            this.scopeKey = new ScopeKey(lValue, lValue.getInferredJavaType().getJavaTypeInstance());
        }

        public JavaTypeInstance getJavaTypeInstance() {
            return lValueType;
        }

        public StatementContainer<StructuredStatement> getStatementContainer() {
            return exactStatement;
        }

        public LocalVariable getlValue() {
            return lValue;
        }

        public int getDepth() {
            return depth;
        }

        public String getName() {
            return name;
        }

        private ScopeKey getScopeKey() {
            return scopeKey;
        }

        private List<StatementContainer<StructuredStatement>> getNestedScope() {
            return nestedScope;
        }
    }

}
