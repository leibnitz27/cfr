package org.benf.cfr.reader.bytecode.analysis.parse.utils.scope;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.util.output.Dumper;

public class LocalClassScopeDiscoverImpl extends AbstractLValueScopeDiscoverer {
    public LocalClassScopeDiscoverImpl(MethodPrototype prototype, VariableFactory variableFactory) {
        super(prototype, variableFactory);
    }

    private static class SentinelNV implements NamedVariable {
        private final JavaTypeInstance typeInstance;

        private SentinelNV(JavaTypeInstance typeInstance) {
            this.typeInstance = typeInstance;
        }

        @Override
        public void forceName(String name) {
        }

        @Override
        public String getStringName() {
            return typeInstance.getRawName();
        }

        @Override
        public boolean isGoodName() {
            return true;
        }

        @Override
        public Dumper dump(Dumper d) {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SentinelNV that = (SentinelNV) o;

            if (typeInstance != null ? !typeInstance.equals(that.typeInstance) : that.typeInstance != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            return typeInstance != null ? typeInstance.hashCode() : 0;
        }
    }

    @Override
    public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value) {
    }

    @Override
    public void collect(LValue lValue) {
        Class<?> lValueClass = lValue.getClass();

        if (lValueClass == SentinelLocalClassLValue.class) {
            SentinelLocalClassLValue localClassLValue = (SentinelLocalClassLValue) lValue;

            NamedVariable name = new SentinelNV(localClassLValue.getLocalClassType());

            ScopeDefinition previousDef = earliestDefinition.get(name);
            // If it's in scope, no problem.
            if (previousDef != null) return;

            JavaTypeInstance type = localClassLValue.getLocalClassType();
            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, currentBlock.peek(), lValue, type, name, currentMark);
            earliestDefinition.put(name, scopeDefinition);
            earliestDefinitionsByLevel.get(currentDepth).put(name, true);
            discoveredCreations.add(scopeDefinition);

        }  else if (lValueClass == FieldVariable.class) {
            lValue.collectLValueUsage(this);
        }
    }

    @Override
    public boolean descendLambdas() {
        return true;
    }
}
