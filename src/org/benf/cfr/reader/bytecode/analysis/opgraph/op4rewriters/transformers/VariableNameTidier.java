package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.variables.Keywords;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 27/08/2013
 * Time: 06:32
 */
public class VariableNameTidier implements StructuredStatementTransformer {

    public void transform(Method method, Op04StructuredStatement root) {
        StructuredScopeWithVars structuredScopeWithVars = new StructuredScopeWithVars();
        structuredScopeWithVars.add(null);
        List<LocalVariable> params = method.getMethodPrototype().getComputedParameters();
        for (LocalVariable param : params) {
            structuredScopeWithVars.defineHere(null, param);
        }
        root.transform(this, structuredScopeWithVars);
    }


    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        StructuredScopeWithVars structuredScopeWithVars = (StructuredScopeWithVars) scope;

        List<LValue> definedHere = in.findCreatedHere();
        if (definedHere != null) {
            for (LValue scopedEntity : definedHere) {
                if (scopedEntity instanceof LocalVariable) {
                    structuredScopeWithVars.defineHere(in, (LocalVariable) scopedEntity);
                }
            }
        }

        in.transformStructuredChildren(this, scope);
        return in;
    }

    private static class StructuredScopeWithVars extends StructuredScope {
        private final LinkedList<AtLevel> scope = ListFactory.newLinkedList();
        private final Map<String, Integer> nextPostFixed = MapFactory.newLazyMap(new UnaryFunction<String, Integer>() {
            @Override
            public Integer invoke(String arg) {
                return 2;
            }
        });

        public void remove(StructuredStatement statement) {
            super.remove(statement);
            scope.removeFirst();
        }

        @Override
        public void add(StructuredStatement statement) {
            super.add(statement);
            scope.addFirst(new AtLevel(statement));
        }

        private boolean alreadyDefined(String name) {
            for (AtLevel atLevel : scope) {
                if (atLevel.isDefinedHere(name)) {
                    return true;
                }
            }
            return false;
        }

        private String getNext(String base) {
            int postfix = nextPostFixed.get(base);
            nextPostFixed.put(base, postfix + 1);
            return base + postfix;
        }

        private String suggestByType(LocalVariable localVariable) {
            JavaTypeInstance type = localVariable.getInferredJavaType().getJavaTypeInstance();

            return type.suggestVarName();
        }

        public void defineHere(StructuredStatement statement, LocalVariable localVariable) {

            NamedVariable namedVariable = localVariable.getName();
            if (!namedVariable.isGoodName()) {
                String suggestion = null;
                if (statement != null) {
                    suggestion = statement.suggestName(localVariable, new Predicate<String>() {
                        @Override
                        public boolean test(String in) {
                            return alreadyDefined(in);
                        }
                    });
                }
                if (suggestion == null) suggestion = suggestByType(localVariable);
                if (suggestion != null) {
                    namedVariable.forceName(suggestion);
                }
            }
            if (Keywords.isAKeyword(namedVariable.getStringName())) {
                namedVariable.forceName(namedVariable.getStringName() + "_");
            }


            /* Check if it's already defined
             *
             */
            final String base = namedVariable.getStringName();
            if (!alreadyDefined(base)) {
                scope.getFirst().defineHere(base);
                return;
            }
            /*
             * Already defined.  Get a new name by incrementing postfix.
             */
            String postfixedVarName;
            do {
                postfixedVarName = getNext(base);
            } while (alreadyDefined(postfixedVarName));
            localVariable.getName().forceName(postfixedVarName);
            scope.getFirst().defineHere(postfixedVarName);
        }

        protected static class AtLevel {
            StructuredStatement statement;
            Set<String> definedHere = SetFactory.newSet();
            int next;

            private AtLevel(StructuredStatement statement) {
                this.statement = statement;
                this.next = 0;
            }

            @Override
            public String toString() {
                return statement.toString();
            }

            public boolean isDefinedHere(String name) {
                return definedHere.contains(name);
            }

            public void defineHere(String name) {
                definedHere.add(name);
            }
        }
    }

}
