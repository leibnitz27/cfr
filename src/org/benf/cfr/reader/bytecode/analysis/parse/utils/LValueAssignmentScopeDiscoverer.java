package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/03/2013
 * Time: 06:09
 */
public class LValueAssignmentScopeDiscoverer implements LValueAssignmentCollector<StructuredStatement> {

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

    private final List<ScopeDefinition> discoveredCreations = ListFactory.newList();

    public void enterBlock() {
        currentDepth++;
    }

    public void leaveBlock() {
        for (String definedHere : earliestDefinitionsByLevel.get(currentDepth)) {
            earliestDefinition.remove(definedHere);
        }
        earliestDefinitionsByLevel.remove(currentDepth);
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
            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, statementContainer, localVariable, value);
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
            throw new UnsupportedOperationException("Type mismatch for local stack re-use");
        }
    }

    public void markDiscoveredCreations() {
        for (ScopeDefinition scopeDefinition : discoveredCreations) {
            StatementContainer<StructuredStatement> statementContainer = scopeDefinition.getStatementContainer();
            statementContainer.getStatement().markCreator(scopeDefinition.getlValue());
        }
    }

    private static class ScopeDefinition {
        private final int depth;
        private final StatementContainer<StructuredStatement> statementContainer;
        private final LocalVariable lValue;
        private final Expression rValue;
        private final JavaTypeInstance lValueType;

        private ScopeDefinition(int depth, StatementContainer<StructuredStatement> statementContainer, LocalVariable lValue, Expression rValue) {
            this.depth = depth;
            this.statementContainer = statementContainer;
            this.lValue = lValue;
            this.rValue = rValue;
            this.lValueType = lValue.getInferredJavaType().getJavaTypeInstance();
        }

        public JavaTypeInstance getJavaTypeInstance() {
            return lValueType;
        }

        public StatementContainer<StructuredStatement> getStatementContainer() {
            return statementContainer;
        }

        public LocalVariable getlValue() {
            return lValue;
        }
    }

}
