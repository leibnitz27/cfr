package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LambdaExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.variables.Keywords;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariableNameTidier implements StructuredStatementTransformer {

    private final Method method;
    private boolean classRenamed = false;

    public VariableNameTidier(Method method) {
        this.method = method;
    }

    public void transform(Op04StructuredStatement root) {
        StructuredScopeWithVars structuredScopeWithVars = new StructuredScopeWithVars();
        structuredScopeWithVars.add(null);
        List<LocalVariable> params = method.getMethodPrototype().getComputedParameters();
        for (LocalVariable param : params) {
            structuredScopeWithVars.defineHere(null, param);
        }
        root.transform(this, structuredScopeWithVars);
    }

    public boolean isClassRenamed() {
        return classRenamed;
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
                if (scopedEntity instanceof SentinelLocalClassLValue) {
                    structuredScopeWithVars.defineLocalClassHere(in, (SentinelLocalClassLValue) scopedEntity);
                }
            }
        }
        /*
         * We have to search expression tree as well, for lambdas.
         */
        ExpressionRewriter expressionRewriter = new ExpressionNameTidier(structuredScopeWithVars);
//        in.rewriteExpressions(expressionRewriter);

        in.transformStructuredChildren(this, scope);
        return in;
    }

    private class ExpressionNameTidier implements ExpressionRewriter {
        private final StructuredScopeWithVars currentScope;

        private ExpressionNameTidier(StructuredScopeWithVars currentScope) {
            this.currentScope = currentScope;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof LambdaExpression) {
                currentScope.add(null);
                List<LValue> lValues = ((LambdaExpression) expression).getArgs();
                for (LValue lValue : lValues) {
                    if (lValue instanceof LocalVariable) {
                        currentScope.defineHere((LocalVariable) lValue);
                    }
                }
                currentScope.remove(null);
            }
            return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return (ConditionalExpression) expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return (AbstractAssignmentExpression) expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return lValue.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return lValue; // shouldn't happen.
        }

        @Override
        public void handleStatement(StatementContainer statementContainer) {

        }
    }

    private class StructuredScopeWithVars extends StructuredScope {
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
            /*
             * If this type is boxed, we can suggest a better name with its unboxed counterpart
             */
            RawJavaType raw = RawJavaType.getUnboxedTypeFor(type);
            if (raw != null) type = raw;
            return type.suggestVarName();
        }

        private String mkLcMojo(String in) {
            return " class!" + in;
        }

        public void defineLocalClassHere(StructuredStatement statement, SentinelLocalClassLValue localVariable) {
            JavaTypeInstance type = localVariable.getLocalClassType();
            String name = type.suggestVarName(); // But upper case first char.
            if (name == null) name = type.getRawName().replace('.', '_'); // mad fallback.
            char[] chars = name.toCharArray();
            for (int idx = 0, len = chars.length; idx < len; ++idx) {
                char c = chars[idx];
                if (c >= '0' && c <= '9') continue;
                chars[idx] = Character.toUpperCase(chars[idx]);
                name = new String(chars, idx, chars.length - idx);
                break;
            }


            String lcMojo = mkLcMojo(name);
            if (!alreadyDefined(lcMojo)) {
                scope.getFirst().defineHere(lcMojo);
                method.markUsedLocalClassType(type, name);
                return;
            }

            String postfixedVarName;
            do {
                postfixedVarName = getNext(name);
            } while (alreadyDefined(mkLcMojo(postfixedVarName)));
            scope.getFirst().defineHere(mkLcMojo(postfixedVarName));
            // TODO : This is bad - passes will interfere with each other!
            method.markUsedLocalClassType(type, postfixedVarName);
            classRenamed = true;
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

            defineHere(localVariable);
        }


        public void defineHere(LocalVariable localVariable) {

            /* Check if it's already defined
             *
             */
            NamedVariable namedVariable = localVariable.getName();
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

        protected class AtLevel {
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
