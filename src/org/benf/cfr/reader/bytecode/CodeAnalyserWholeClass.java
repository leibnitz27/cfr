package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.DeadMethodRemover;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.ConstructorUtils;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.ClassCache;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Set;

/**
 * Analysis which needs to be performed on the whole classfile in one go, once we've
 * performed other basic code analysis.
 * <p/>
 */
public class CodeAnalyserWholeClass {
    /*
     * This pass is performed INNER CLASS FIRST.
     */
    public static void wholeClassAnalysisPass1(ClassFile classFile, DCCommonState state) {
        Options options = state.getOptions();

        /*
         * Whole class analysis / transformation - i.e. if it's an enum class, we will need to rewrite
         * several methods.
         */
        EnumClassRewriter.rewriteEnumClass(classFile, state);

        /* Remove generics which 'don't belong here' - i.e. ones which we brought in for analysis, but have
         * ended up in the body of the code.
         *
         * (sign of this would be Map<K,V> etc hanging around).
         */
        if (options.getOption(OptionsImpl.REMOVE_BAD_GENERICS)) {
            removeIllegalGenerics(classFile, options);
        }

        if (options.getOption(OptionsImpl.SUGAR_ASSERTS)) {
            resugarAsserts(classFile, options);
        }

        if (options.getOption(OptionsImpl.LIFT_CONSTRUCTOR_INIT)) {
            liftStaticInitialisers(classFile, options);
            liftNonStaticInitialisers(classFile, options);
        }

        if (options.getOption(OptionsImpl.JAVA_4_CLASS_OBJECTS, classFile.getClassFileVersion())) {
            resugarJava14classObjects(classFile, state);
        }

        if (options.getOption(OptionsImpl.REMOVE_BOILERPLATE)) {
            removeBoilerplateMethods(classFile);
        }

        if (options.getOption(OptionsImpl.REMOVE_INNER_CLASS_SYNTHETICS)) {
            if (classFile.isInnerClass()) {
                removeInnerClassOuterThis(classFile);
            }
            // Synthetic constructor friends can exist on OUTER classes, when an inner makes a call out.
            removeInnerClassSyntheticConstructorFriends(classFile);
        }

    }

    private static void replaceNestedSyntheticOuterRefs(ClassFile classFile) {
        for (Method method : classFile.getMethods()) {
            if (method.hasCodeAttribute()) {
                Op04StructuredStatement code = method.getAnalysis();
                Op04StructuredStatement.replaceNestedSyntheticOuterRefs(code);
            }
        }
    }

    private static void inlineAccessors(DCCommonState state, ClassFile classFile) {
        for (Method method : classFile.getMethods()) {
            if (method.hasCodeAttribute()) {
                Op04StructuredStatement code = method.getAnalysis();
                Op04StructuredStatement.inlineSyntheticAccessors(state, method, code);
            }
        }
    }

    private static void renameAnonymousScopeHidingVariables(ClassFile classFile, ClassCache classCache) {
        List<ClassFileField> fields = Functional.filter(classFile.getFields(), new Predicate<ClassFileField>() {
            @Override
            public boolean test(ClassFileField in) {
                return in.isSyntheticOuterRef();
            }
        });
        if (fields.isEmpty()) return;


        for (Method method : classFile.getMethods()) {
            if (method.hasCodeAttribute()) {
                /*
                 * Construct a renamer - gather names from prototype and from locals assigned in the code.
                 * Make sure that they don't hide the outer variable.
                 */
                ScopeHidingVariableRewriter rewriter = new ScopeHidingVariableRewriter(fields, method, classCache);
                rewriter.rewrite(method.getAnalysis());
            }
        }
    }

    /*
     * Fix references to this$x etc
     */
    private static void fixInnerClassConstructorSyntheticOuterArgs(ClassFile classFile) {
        for (Method method : classFile.getConstructors()) {
            Op04StructuredStatement.fixInnerClassConstructorSyntheticOuterArgs(classFile, method, method.getAnalysis());
        }
    }

    /*
     * Friend accessors tack an extra, hidden argument onto the end of a synthetic
     * constructor to make sure that only 'friends' can access it.
     *
     * Find these, and mark them as hidden.  This constructor should forward to an
     * identical private one, minus the last argument.
     */
    private static void removeInnerClassSyntheticConstructorFriends(ClassFile classFile) {
        for (Method method : classFile.getConstructors()) {
            Set<AccessFlagMethod> flags = method.getAccessFlags();
            if (!flags.contains(AccessFlagMethod.ACC_SYNTHETIC)) continue;
            if (flags.contains(AccessFlagMethod.ACC_PUBLIC)) continue;

            MethodPrototype chainPrototype =  ConstructorUtils.getDelegatingPrototype(method);
            if (chainPrototype == null) continue;
            // Verify that the target is identical to this, minus last arg.
            MethodPrototype prototype = method.getMethodPrototype();

            List<JavaTypeInstance> argsThis = prototype.getArgs();
            if (argsThis.isEmpty()) continue;
            List<JavaTypeInstance> argsThat = chainPrototype.getArgs();
            if (argsThis.size() != argsThat.size() + 1) continue;
            JavaTypeInstance last = argsThis.get(argsThis.size()-1);

            UnaryFunction<JavaTypeInstance, JavaTypeInstance> degenerifier = new UnaryFunction<JavaTypeInstance, JavaTypeInstance>() {
                @Override
                public JavaTypeInstance invoke(JavaTypeInstance arg) {
                    return arg.getDeGenerifiedType();
                }
            };
            argsThis = Functional.map(argsThis, degenerifier);
            argsThat = Functional.map(argsThat, degenerifier);
            argsThis.remove(argsThis.size()-1);


            /*
             * Compare the types.  However, we compare AFTER ERASURE, as there's no
             * need for the compiler to emit a generic signature for the synthetic method.
             */
            if (!argsThis.equals(argsThat)) continue;

            /*
             * This is a synthetic non public constructor, which forwards to another constructor minus
             * a single terminal argument.  If this argument is an inner class of one of our outers (or us)
             * we assume it's a fake.
             *
             * This could be tricked.....
             */
            InnerClassInfo innerClassInfo = last.getInnerClassHereInfo();
            if (!innerClassInfo.isInnerClass()) continue;

            innerClassInfo.hideSyntheticFriendClass();
            prototype.hide(argsThis.size());
            method.hideSynthetic();
        }
    }

    /*
     * Remove the first argument from inner class constructors.
     *
     * We expect that ALL constructors will have the same argument removed - if that's the case
     * then we mark that as a synthetic outer.
     */
    private static void removeInnerClassOuterThis(ClassFile classFile) {

        if (classFile.testAccessFlag(AccessFlag.ACC_STATIC)) return;

        /*
         * First pass - verify that all constructors either have an outer arg,
         * or are chained constructors.  If they're chained constructors, they should
         * have the outer arg, but we can't verify that they assign to the field.
         */
        FieldVariable foundOuterThis = null;
        for (Method method : classFile.getConstructors()) {
            if (ConstructorUtils.isDelegating(method)) continue;
            FieldVariable outerThis = Op04StructuredStatement.findInnerClassOuterThis(method, method.getAnalysis());
            if (outerThis == null) return;
            if (foundOuterThis == null) {
                foundOuterThis = outerThis;
            } else if (foundOuterThis != outerThis) {
                return;
            }
        }
        if (foundOuterThis == null) return;

        ClassFileField classFileField = foundOuterThis.getClassFileField();
        classFileField.markHidden();
        classFileField.markSyntheticOuterRef();

        for (Method method : classFile.getConstructors()) {
            if (ConstructorUtils.isDelegating(method)) {
                // TODO: This is a bit brittle.
                MethodPrototype prototype = method.getMethodPrototype();
                prototype.setInnerOuterThis();
                prototype.hide(0);
            }
            Op04StructuredStatement.removeInnerClassOuterThis(method, method.getAnalysis());
        }


        String originalName = foundOuterThis.getFieldName();
        /*
         * FieldVariable here is a 'local' one - it has an expression object of 'this'.
         *
         * Find all instances of 'this'.fieldVariable in the class, and replace with
         * OuterClassName.this
         */
        JavaTypeInstance fieldType = foundOuterThis.getInferredJavaType().getJavaTypeInstance();
        if (!(fieldType instanceof JavaRefTypeInstance)) {
            return;
        }
        JavaRefTypeInstance fieldRefType = (JavaRefTypeInstance) fieldType.getDeGenerifiedType();
        String name = fieldRefType.getRawShortName();
        classFileField.overrideName(name + ".this");
        classFileField.markSyntheticOuterRef();
        /*
         * TODO :
         * This is a bit of a hack - we may be referring to a classfile field from a partially analysed
         * class.  So replace the local one with the field variable one.
         */
        try {
            ClassFileField localClassFileField = classFile.getFieldByName(originalName, fieldType);
            localClassFileField.overrideName(name + ".this");
            localClassFileField.markSyntheticOuterRef();
        } catch (NoSuchFieldException e) {
        }
        classFile.getClassType().getInnerClassHereInfo().setHideSyntheticThis();
    }

    private static Method getStaticConstructor(ClassFile classFile) {
        Method staticInit;
        try {
            staticInit = classFile.getMethodByName(MiscConstants.STATIC_INIT_METHOD).get(0);
        } catch (NoSuchMethodException e) {
            return null;
        }
        return staticInit;
    }

    /* As much as possible, lift code from a <clinit> method into the declarations.
     * Because we can put arbitrary code in a clinit, this isn't always possible, however
     * we want to try because
     * a) it looks tidier!
     * b) interfaces MAY have static initialisers, but MAY NOT have clinit methods.
     *    (in java 1.7)
     */
    private static void liftStaticInitialisers(ClassFile classFile, Options state) {
        Method staticInit = getStaticConstructor(classFile);
        if (staticInit == null) return;
        new StaticLifter(classFile).liftStatics(staticInit);
    }

    private static void liftNonStaticInitialisers(ClassFile classFile, Options state) {
        new NonStaticLifter(classFile).liftNonStatics();
    }

    /*
     * Some methods can be completely removed if they're empty other than comments.
     *
     * default constructor
     * static constructor
     *
     * Obviously, this step has to come AFTER any constructor rewriting (static lifting)
     */
    private static void removeDeadMethods(ClassFile classFile) {
        Method staticInit = getStaticConstructor(classFile);
        if (staticInit != null) {
            DeadMethodRemover.removeDeadMethod(classFile, staticInit);
        }

        // If there's only one constructor, and it's the default (0 args, public, non final)
        // with no code, we can remove it.
        tryRemoveConstructor(classFile);
    }

    private static void removeBoilerplateMethods(ClassFile classFile) {
        String[] removeThese = {MiscConstants.DESERIALISE_LAMBDA_METHOD};
        for (String methName : removeThese) {
            List<Method> methods = classFile.getMethodsByNameOrNull(methName);
            if (methods != null) {
                for (Method method : methods) {
                    method.hideSynthetic();
                }
            }
        }
    }

    private static void tryRemoveConstructor(ClassFile classFile) {
        List<Method> constructors = classFile.getConstructors();
        if (constructors.size() != 1) return;
        Method constructor = constructors.get(0);

        // 0 args.
        MethodPrototype methodPrototype = constructor.getMethodPrototype();
        if (methodPrototype.getVisibleArgCount() > 0) return;
        // public, non final.
        if (constructor.testAccessFlag(AccessFlagMethod.ACC_FINAL)) return;
        if (!constructor.testAccessFlag(AccessFlagMethod.ACC_PUBLIC)) return;

        if (!MiscStatementTools.isDeadCode(constructor.getAnalysis())) return;
        classFile.removePointlessMethod(constructor);
    }

    /* Performed prior to lifting code into fields, just check code */
    private static void removeIllegalGenerics(ClassFile classFile, Options state) {
        ConstantPool cp = classFile.getConstantPool();
        ExpressionRewriter r = new IllegalGenericRewriter(cp);

        for (Method m : classFile.getMethods()) {
            if (!m.hasCodeAttribute()) return;
            Op04StructuredStatement code = m.getAnalysis();
            if (!code.isFullyStructured()) continue;

            List<StructuredStatement> statements = MiscStatementTools.linearise(code);
            if (statements == null) return;

            for (StructuredStatement statement : statements) {
                statement.rewriteExpressions(r);
            }
            /*
             * Apply boxing rewriter once more as well, to get rid of anything that's occured.
             */
            Op04StructuredStatement.removePrimitiveDeconversion(state, m, code);
        }
    }


    private static void resugarAsserts(ClassFile classFile, Options state) {
        Method staticInit = getStaticConstructor(classFile);
        if (staticInit != null) {
            new AssertRewriter(classFile).sugarAsserts(staticInit);
        }
    }

    private static void resugarJava14classObjects(ClassFile classFile, DCCommonState state) {
        new J14ClassObjectRewriter(classFile, state).rewrite();
    }

    /*
     * This pass is performed INNER CLASS LAST.
     *
     * This is the point at which we can perform analysis like rewriting references like accessors inner -> outer.
     */
    public static void wholeClassAnalysisPass2(ClassFile classFile, DCCommonState state) {
        Options options = state.getOptions();
        /*
         * Rewrite 'outer.this' references.
         */
        if (options.getOption(OptionsImpl.REMOVE_INNER_CLASS_SYNTHETICS)) {

            /*
             * All constructors of inner classes should have their first argument removed,
             * and it should be marked as hidden.
             */
            if (classFile.isInnerClass()) {
                fixInnerClassConstructorSyntheticOuterArgs(classFile);
            }

            replaceNestedSyntheticOuterRefs(classFile);

            inlineAccessors(state, classFile);

            /*
             * Rename anonymous and method scoped inner variables which inadvertently hide outer class
             * variables.
             */
            renameAnonymousScopeHidingVariables(classFile, state.getClassCache());
        }


        if (options.getOption(OptionsImpl.REMOVE_DEAD_METHODS)) {
            removeDeadMethods(classFile);
        }

    }
}