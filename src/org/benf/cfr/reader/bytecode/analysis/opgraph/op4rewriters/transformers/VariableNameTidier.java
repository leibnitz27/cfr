package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.BytecodeMeta;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LambdaExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.variables.Keywords;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.ClassCache;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public class VariableNameTidier implements StructuredStatementTransformer {

    private final Method method;
    private boolean classRenamed = false;
    // Used for detecting static accesses we can mark as accessible via the simple name.
    private final JavaTypeInstance ownerClassType;
    private final Set<String> bannedNames;
    private final ClassCache classCache;

    public VariableNameTidier(Method method, Set<String> bannedNames, ClassCache classCache) {
        this.method = method;
        this.ownerClassType = method.getClassFile().getClassType();
        this.bannedNames = bannedNames;
        this.classCache = classCache;
    }

    public VariableNameTidier(Method method, ClassCache classCache) {
        this(method, new HashSet<String>(), classCache);
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

    /*
     * One pass to collect all the variables that are used, and a second to rename the bad ones.
     * We do this so that legitimate name introductions for top level variables don't cause inner scope
     * ones to be renamed as collisions.
     *
     * { int BADNAME;
     * for (int a=0;a<10;++a);
     * for (int a=0;a<10;++a); // shouldn't rename BADNAME -> a.
     */
    public static class NameDiscoverer extends AbstractExpressionRewriter implements StructuredStatementTransformer {
        private final Set<String> usedNames = SetFactory.newSet();
        private static final Set<String> EMPTY = SetFactory.newSet();

        private NameDiscoverer() {
        }


        private void addLValues(Collection<LValue> definedHere) {
            if (definedHere == null) return;
            for (LValue scopedEntity : definedHere) {
                if (scopedEntity instanceof LocalVariable) {
                    NamedVariable namedVariable = ((LocalVariable) scopedEntity).getName();
                    usedNames.add(namedVariable.getStringName());
                }
            }
        }

        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            /*
             * Check if this statement has any embedded lambdas, as these can define clashing scopes.
             * Note that we /WONT/ collect these as definedHere, as they don't ever generate or escape
             * a scope.
             */
            in.rewriteExpressions(this);

            in.transformStructuredChildren(this, scope);
            return in;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof LambdaExpression) {
                addLValues(((LambdaExpression) expression).getArgs());
                return expression;
            } else {
                return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
            }

        }

        public static Set<String> getUsedLambdaNames(BytecodeMeta bytecodeMeta, Op04StructuredStatement in) {
            if (!bytecodeMeta.has(BytecodeMeta.CodeInfoFlag.USES_INVOKEDYNAMIC)) {
                return EMPTY;
            }
            NameDiscoverer discoverer = new NameDiscoverer();
            in.transform(discoverer, new StructuredScope());
            return discoverer.usedNames;
        }
    }

    public void renameToAvoidHiding(Set<String> avoid, List<LocalVariable> collisions) {
        StructuredScopeWithVars structuredScopeWithVars = new StructuredScopeWithVars();
        structuredScopeWithVars.add(null);
        structuredScopeWithVars.markInitiallyDefined(avoid);
        for (LocalVariable hider : collisions) {
            // This will cause it to be renamed.
            structuredScopeWithVars.defineHere(hider);
        }
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
                    structuredScopeWithVars.defineLocalClassHere((SentinelLocalClassLValue) scopedEntity);
                }
            }
        }

        /*
         * See if there's anything USED here which could be tidied - e.g. if we're using a (class local) static
         * variable, and there's nothing in scope which hides it, then we should use the simple name.
         * (this is indeed necessary in the case of static final variables in the static initialiser).
         */
        ExpressionRewriter simplifier = new NameSimplifier(ownerClassType, structuredScopeWithVars);
        in.rewriteExpressions(simplifier);

        in.transformStructuredChildren(this, scope);
        return in;
    }

    private static class NameSimplifier extends AbstractExpressionRewriter {
        private final StructuredScopeWithVars localScope;
        private final JavaTypeInstance ownerClassType;

        private NameSimplifier(JavaTypeInstance ownerClassType, StructuredScopeWithVars localScope) {
            this.ownerClassType = ownerClassType;
            this.localScope = localScope;
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (lValue.getClass() == StaticVariable.class) {
                StaticVariable staticVariable = (StaticVariable) lValue;
                String fieldName = staticVariable.getFieldName();
                if (!localScope.isDefined(fieldName)) {
                    JavaTypeInstance owningClassType = staticVariable.getOwningClassType();
                    if (owningClassType.equals(ownerClassType)) {
                        if (!MiscConstants.CLASS.equals(fieldName)) {
                            return staticVariable.getSimpleCopy();
                        }
                    } else {
                        // From Java12 and up, we generate direct accessors to private static variables in outer classes!
                        InnerClassInfo innerClassInfo = ownerClassType.getInnerClassHereInfo();
                        while (innerClassInfo.isInnerClass()) {
                            JavaRefTypeInstance clazz = innerClassInfo.getOuterClass();
                            ClassFile classFile = clazz.getClassFile();
                            if (classFile == null) break;
                            if (owningClassType.equals(clazz)) {
                                return staticVariable.getSimpleCopy();
                            }
                            if (classFile.hasLocalField(fieldName)) break;
                            innerClassInfo = clazz.getInnerClassHereInfo();
                        }
                    }
                }
            }
            return lValue;
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
            return alreadyDefined(name, true);
        }

        private boolean alreadyDefined(String name, boolean checkClassCache ) {
            if (bannedNames.contains(name)) return true;
            for (AtLevel atLevel : scope) {
                if (atLevel.isDefinedHere(name)) {
                    return true;
                }
            }
            /*
             * And check that it doesn't collide with any of the known class names!
             */
            if (checkClassCache && classCache.isClassName(name)) {
                return true;
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

        void defineLocalClassHere(SentinelLocalClassLValue localVariable) {
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

        void defineHere(StructuredStatement statement, LocalVariable localVariable) {

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
                    if (suggestion.length() == 1 && suggestion.toUpperCase().equals(suggestion)) suggestion = suggestion.toLowerCase();
                    namedVariable.forceName(suggestion);
                }
            }
            if (Keywords.isAKeyword(namedVariable.getStringName())) {
                // it is common amongst java users to replace s with z to avoid reserved keywords
                // very common example: clazz instead of class
                String transposed = namedVariable.getStringName().replace("s", "z");
                if (Keywords.isAKeyword(transposed)
                    namedVariable.forceName("_" + transposed);
                else
                    namedVariable.forceName(transposed);
            }

            defineHere(localVariable);
        }

        void markInitiallyDefined(Set<String> names) {
            for (String name : names) scope.getFirst().defineHere(name);
        }

        boolean isDefined(String anyNameType) {
            return alreadyDefined(anyNameType, false);
        }

        void defineHere(LocalVariable localVariable) {

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

            boolean isDefinedHere(String name) {
                return definedHere.contains(name);
            }

            void defineHere(String name) {
                definedHere.add(name);
            }
        }
    }

}
