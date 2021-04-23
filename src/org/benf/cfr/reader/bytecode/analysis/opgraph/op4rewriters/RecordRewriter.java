package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.ConstructorUtils;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewAnonymousArray;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperRecord;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodHandle;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.MiscUtils;
import org.benf.cfr.reader.util.Optional;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RecordRewriter {
    private static Set<AccessFlag> recordFieldFlags = SetFactory.newSet(AccessFlag.ACC_FINAL, AccessFlag.ACC_PRIVATE);
    private static Set<AccessFlagMethod> recordGetterFlags = SetFactory.newSet(AccessFlagMethod.ACC_PUBLIC);

    public static void rewrite(ClassFile classFile, DCCommonState state) {
        if (!TypeConstants.RECORD.equals(classFile.getBaseClassType())) return;

        /* For a record, we can eliminate
         * * the constructor (if it only assigns fields, and assigns ALL fields)
         *    (but we must leave validation code in place).
         * * equals, hashcode and toString, if they exactly match the expected bootstrap pattern.
         * * getters (if they are exactly getters).
         */
        rewriteIfRecord(classFile, state);
    }

    private static boolean rewriteIfRecord(ClassFile classFile, DCCommonState state) {
        List<ClassFileField> instances = Functional.filter(classFile.getFields(), new Predicate<ClassFileField>() {
            @Override
            public boolean test(ClassFileField in) {
                return !in.getField().testAccessFlag(AccessFlag.ACC_STATIC);
            }
        });

        if (!Functional.filter(instances, new Predicate<ClassFileField>() {
            @Override
            public boolean test(ClassFileField in) {
                return !recordFieldFlags.equals(in.getField().getAccessFlags());
            }
        }).isEmpty()) {
            // we have some fields that don't look right.
            return false;
        }

        // getters don't have to be default - we can hide them if they ARE default getters, but
        // it's not required.
        // However, the 0 argument getter for each field has to exist, AND it has to be public.
        List<Method> getters = ListFactory.newList();
        for (ClassFileField cff : instances) {
            List<Method> methods = classFile.getMethodsByNameOrNull(cff.getFieldName());
            // TODO: Rather than search for the method to match the field, we could
            // use the method as the source of truth.  However, there's a problem here, as
            // we can add additional methods which are not related.
            if (methods == null) return false;
            methods = Functional.filter(methods, new Predicate<Method>() {
                @Override
                public boolean test(Method in) {
                    return in.getMethodPrototype().getArgs().isEmpty();
                }
            });
            if (methods.size() != 1) return false;
            Method method = methods.get(0);
            if (!recordGetterFlags.equals(method.getAccessFlags())) {
                return false;
            }
            getters.add(method);
        }

        // There must be one canonical constructor, and all other constructors must
        // call it (eventually)
        // (the latter can be cheekily verified by ensuring all other constructors are chained to SOMETHING).
        List<Method> constructors = classFile.getConstructors();
        Pair<List<Method>, List<Method>> splitCons = Functional.partition(constructors, new IsCanonicalConstructor(instances));
        if (splitCons.getFirst().size() != 1) {
            // incorrect implicit.
            return false;
        }
        Method canonicalCons = splitCons.getFirst().get(0);

        Method.MethodConstructor constructorFlag = canonicalCons.getConstructorFlag();
        if (!constructorFlag.isConstructor()) return false;
        if (constructorFlag.isEnumConstructor()) return false;

        List<Method> otherCons = splitCons.getSecond();
        for (Method other : otherCons) {
            MethodPrototype chain = ConstructorUtils.getDelegatingPrototype(other);
            if (chain == null) return false;
        }

        JavaRefTypeInstance thisType = classFile.getRefClassType();
        // At this point, we're going to proceed, so can make changes.

        // Assignments from the parameters to the members should be at the end, in which case
        // they can hidden / removed.
        // If there is no code after this, the canonical can be hidden as implicit.
        removeImplicitAssignments(canonicalCons, instances, thisType);
        // Mark the canonical constructor for later.
        canonicalCons.setConstructorFlag(Method.MethodConstructor.RECORD_CANONICAL_CONSTRUCTOR);
        // Now if it's empty, hide altogether.
        hideConstructorIfEmpty(canonicalCons);

        // If any of the getters are default getters, hide them also.
        for (int x=0;x<getters.size();++x) {
            hideDefaultGetter(getters.get(x), instances.get(x), thisType);
        }

        hideDefaultUtilityMethods(classFile, thisType, instances);
        classFile.setDumpHelper(new ClassFileDumperRecord(state));
        return true;
    }

    /*
     * Default implementations of
     * equals
     * hashcode
     * toString
     *
     * can be hidden.
     *
     * NB: This default may change - this is the existing behaviour as of 14-ea+34-1452
     */
    private static void hideDefaultUtilityMethods(ClassFile classFile, JavaTypeInstance thisType, List<ClassFileField> instances) {
        hideEquals(classFile, thisType, instances);
        hideToString(classFile, thisType, instances);
        hideHashCode(classFile, thisType, instances);
    }

    private static void hideEquals(ClassFile classFile, JavaTypeInstance thisType, List<ClassFileField> fields) {
        Method method = getMethod(classFile, Collections.<JavaTypeInstance>singletonList(TypeConstants.OBJECT), MiscConstants.EQUALS);
        if (method == null) return;

        WildcardMatch wcm = new WildcardMatch();
        StructuredStatement stm = new StructuredReturn(BytecodeLoc.NONE, new CastExpression(BytecodeLoc.NONE, new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.TEST),
                wcm.getStaticFunction("func", TypeConstants.OBJECTMETHODS, TypeConstants.OBJECT, "bootstrap",
                        new Literal(TypedLiteral.getString(QuotingUtils.enquoteString(MiscConstants.EQUALS))),
                        wcm.getExpressionWildCard("array"),
                        wcm.getExpressionWildCard("this"),
                        new LValueExpression(method.getMethodPrototype().getComputedParameters().get(0)))), RawJavaType.BOOLEAN);

        hideIfMatch(thisType, fields, method, wcm, stm);
    }

    private static void hideToString(ClassFile classFile, JavaTypeInstance thisType, List<ClassFileField> fields) {
        Method method = getMethod(classFile, Collections.<JavaTypeInstance>emptyList(), MiscConstants.TOSTRING);
        if (method == null) return;

        WildcardMatch wcm = new WildcardMatch();
        StructuredStatement stm = new StructuredReturn(BytecodeLoc.NONE,
                wcm.getStaticFunction("func", TypeConstants.OBJECTMETHODS, TypeConstants.OBJECT, "bootstrap",
                        new Literal(TypedLiteral.getString(QuotingUtils.enquoteString(MiscConstants.TOSTRING))),
                        wcm.getExpressionWildCard("array"),
                        wcm.getExpressionWildCard("this")), TypeConstants.STRING);

        hideIfMatch(thisType, fields, method, wcm, stm);
    }

    private static void hideHashCode(ClassFile classFile, JavaTypeInstance thisType, List<ClassFileField> fields) {
        Method method = getMethod(classFile, Collections.<JavaTypeInstance>emptyList(), MiscConstants.HASHCODE);
        if (method == null) return;

        WildcardMatch wcm = new WildcardMatch();
        StructuredStatement stm = new StructuredReturn(BytecodeLoc.NONE, new CastExpression(BytecodeLoc.NONE, new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.TEST),
                wcm.getStaticFunction("func", TypeConstants.OBJECTMETHODS, TypeConstants.OBJECT, "bootstrap",
                        new Literal(TypedLiteral.getString(QuotingUtils.enquoteString(MiscConstants.HASHCODE))),
                        wcm.getExpressionWildCard("array"),
                        wcm.getExpressionWildCard("this"))), RawJavaType.INT);

        hideIfMatch(thisType, fields, method, wcm, stm);
    }

    private static void hideIfMatch(JavaTypeInstance thisType, List<ClassFileField> fields, Method method, WildcardMatch wcm, StructuredStatement stm) {
        StructuredStatement item = getSingleCodeLine(method);
        if (!stm.equals(item)) return;
        if (!cmpArgsEq(wcm.getExpressionWildCard("array").getMatch(), thisType, fields)) return;
        if (!MiscUtils.isThis(wcm.getExpressionWildCard("this").getMatch(), thisType)) return;
        method.hideDead();
    }

    private static boolean stringArgEq(Expression expression, String name) {
        Literal l = expression.getComputedLiteral(null);
        if (l == null) return false;
        TypedLiteral tl = l.getValue();
        if (tl.getType() != TypedLiteral.LiteralType.String) return false;
        String val = tl.toString();
        return val.equals(QuotingUtils.enquoteString(name));
    }

    private static boolean methodHandleEq(Expression expression, String name) {
        Literal l = expression.getComputedLiteral(null);
        if (l == null) return false;
        TypedLiteral tl = l.getValue();
        if (tl.getType() != TypedLiteral.LiteralType.MethodHandle) return false;
        ConstantPoolEntryMethodHandle handle = tl.getMethodHandle();
        if (!handle.isFieldRef()) return false;
        String fName = handle.getFieldRef().getLocalName();
        return name.equals(fName);
    }

    private static boolean classArgEq(Expression expression, JavaTypeInstance thisType) {
        Literal l = expression.getComputedLiteral(null);
        if (l == null) return false;
        TypedLiteral tl = l.getValue();
        if (tl.getType() != TypedLiteral.LiteralType.Class) return false;
        return thisType.equals(tl.getClassValue());
    }

    private static boolean cmpArgsEq(Expression cmpArgs, JavaTypeInstance thisType, List<ClassFileField> instances) {
        if (!(cmpArgs instanceof NewAnonymousArray)) return false;
        List<Expression> cmpValues = ((NewAnonymousArray) cmpArgs).getValues();
        if (cmpValues.size() != instances.size() + 2) return false;

        if (!classArgEq(cmpValues.get(0), thisType)) return false;

        StringBuilder semi = new StringBuilder();
        int idx = 2;
        for (ClassFileField field : instances) {
            if (idx != 2) semi.append(";");
            Expression arg = cmpValues.get(idx++);
            String name = field.getFieldName();
            if (!methodHandleEq(arg, name)) return false;
            semi.append(name);
        }
        if (!stringArgEq(cmpValues.get(1), semi.toString())) return false;
        return true;
    }

    private static Method getMethod(ClassFile classFile, final List<JavaTypeInstance> args, String name) {
        List<Method> methods = classFile.getMethodsByNameOrNull(name);
        if (methods == null) return null;
        methods = Functional.filter(methods, new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                if (!in.testAccessFlag(AccessFlagMethod.ACC_PUBLIC)) return false;
                return in.getMethodPrototype().getArgs().equals(args);
            }
        });
        return methods.size() == 1 ? methods.get(0) : null;
    }

    private static StructuredStatement getSingleCodeLine(Method method) {
        if (method == null) return null;
        if (method.getCodeAttribute() == null) return null;
        Op04StructuredStatement code = method.getAnalysis();
        StructuredStatement topCode = code.getStatement();
        if (!(topCode instanceof Block)) return null;
        Block block = (Block)topCode;
        Optional<Op04StructuredStatement> content = block.getMaybeJustOneStatement();
        if (!content.isSet()) return null;
        return content.getValue().getStatement();
    }

    private static void hideDefaultGetter(Method method, ClassFileField classFileField, JavaRefTypeInstance thisType) {
        StructuredStatement item = getSingleCodeLine(method);
        if (item == null) return;
        WildcardMatch wcm = new WildcardMatch();
        StructuredStatement s = new StructuredReturn(BytecodeLoc.NONE, new LValueExpression(wcm.getLValueWildCard("var")), classFileField.getField().getJavaTypeInstance());
        if (!s.equals(item)) return;
        ClassFileField cff = getCFF(wcm.getLValueWildCard("var").getMatch(), thisType);
        if (cff != classFileField) return;
        classFileField.markHidden();
        method.hideDead();
    }

    private static void hideConstructorIfEmpty(Method canonicalCons) {
        if (canonicalCons.getCodeAttribute() == null) return;
        Op04StructuredStatement code = canonicalCons.getAnalysis();
        if (code.getStatement().isEffectivelyNOP()) {
            canonicalCons.hideDead();
        }
    }

    private static void removeImplicitAssignments(Method canonicalCons, List<ClassFileField> instances, JavaRefTypeInstance thisType) {
        if (canonicalCons.getCodeAttribute() == null) return;
        Op04StructuredStatement code = canonicalCons.getAnalysis();

        instances = ListFactory.newList(instances);

        List<LocalVariable> args = canonicalCons.getMethodPrototype().getComputedParameters();
        // We expect a block.  The last N statements should be assignments
        StructuredStatement topCode = code.getStatement();
        if (!(topCode instanceof Block)) return;
        Block block = (Block)topCode;

        List<Op04StructuredStatement> statements = block.getBlockStatements();
        for (int x=statements.size()-1;x>=0;x--) {
            Op04StructuredStatement stm = statements.get(x);
            StructuredStatement statement = stm.getStatement();
            // this is very messy - refactor using wildcardmatch.
            if (statement.isEffectivelyNOP()) continue;
            if (!(statement instanceof StructuredAssignment)) return;
            LValue lhs = ((StructuredAssignment) statement).getLvalue();
            ClassFileField field = getCFF(lhs, thisType);
            if (field == null) return;
            int idx = instances.indexOf(field);
            if (idx == -1) return;
            instances.set(idx, null);

            Expression rhs = ((StructuredAssignment) statement).getRvalue();
            if (!(rhs instanceof LValueExpression)) return;
            LValue rlv = ((LValueExpression) rhs).getLValue();
            LocalVariable expected = args.get(idx);
            if (rlv != expected) return;
            stm.nopOut();
        }
    }

    private static ClassFileField getCFF(LValue lhs, JavaRefTypeInstance thisType) {
        if (!(lhs instanceof FieldVariable)) return null;
        Expression obj = ((FieldVariable) lhs).getObject();
        if (!MiscUtils.isThis(obj, thisType)) return null;
        return ((FieldVariable) lhs).getClassFileField();
    }

    static class IsCanonicalConstructor implements Predicate<Method> {
        private final List<ClassFileField> fields;

        IsCanonicalConstructor(List<ClassFileField> fields) {
            this.fields = fields;
        }

        @Override
        public boolean test(Method in) {
            MethodPrototype proto = in.getMethodPrototype();
            if (!proto.parametersComputed()) return false;
            List<JavaTypeInstance> protoArgs = proto.getArgs();
            if (protoArgs.size() != fields.size()) return false;
            List<LocalVariable> parameters = proto.getComputedParameters();
            // The names MIGHT not match, if we've been obfuscated.  That's ok, as long as the parameters are assigned,
            // at the top level, to the appropriate variable.
            if (parameters.size() != fields.size()) return false;
            // If they don't match, we have to rename, as implicit parameters are usable inside constructor.
            for (int x=0;x<fields.size();++x) {
                JavaTypeInstance fieldType = fields.get(x).getField().getJavaTypeInstance();
                JavaTypeInstance paramType = protoArgs.get(x);
                if (!fieldType.equals(paramType)) return false;
            }

            return true;
        }
    }
}
