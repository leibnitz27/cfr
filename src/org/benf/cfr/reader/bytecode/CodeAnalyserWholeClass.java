package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.DeadMethodRemover;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/05/2013
 * Time: 06:22
 * <p/>
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
        EnumClassRewriter.rewriteEnumClass(classFile, options);

        /* Remove generics which 'don't belong here' - i.e. ones which we brought in for analysis, but have
         * ended up in the body of the code.
         *
         * (sign of this would be Map<K,V> etc hanging around).
         */
        if (options.getBooleanOpt(OptionsImpl.REMOVE_BAD_GENERICS)) {
            removeIllegalGenerics(classFile, options);
        }

        if (options.getBooleanOpt(OptionsImpl.SUGAR_ASSERTS)) {
            resugarAsserts(classFile, options);
        }

        if (options.getBooleanOpt(OptionsImpl.LIFT_CONSTRUCTOR_INIT)) {
            liftStaticInitialisers(classFile, options);
            liftNonStaticInitialisers(classFile, options);
        }

        if (options.getBooleanOpt(OptionsImpl.REMOVE_BOILERPLATE)) {
            removeBoilerplateMethods(classFile);
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

    private static void fixInnerClassConstructors(ClassFile classFile) {

        if (classFile.testAccessFlag(AccessFlag.ACC_STATIC)) return;

        Set<LValue> removedLValues = SetFactory.newSet();
        boolean invalid = false;
        for (Method method : classFile.getConstructors()) {
            LValue removed = Op04StructuredStatement.fixInnerClassConstruction(method, method.getAnalysis());
            if (removed == null) {
                invalid = true;
            } else {
                removedLValues.add(removed);
            }
        }
        if (invalid || removedLValues.size() != 1) return;

        LValue outerThis = removedLValues.iterator().next();
        if (!(outerThis instanceof FieldVariable)) return;

        FieldVariable fieldVariable = (FieldVariable) outerThis;
        String originalName = fieldVariable.getFieldName();
        /*
         * FieldVariable here is a 'local' one - it has an expression object of 'this'.
         *
         * Find all instances of 'this'.fieldVariable in the class, and replace with
         * OuterClassName.this
         */
        JavaTypeInstance fieldType = outerThis.getInferredJavaType().getJavaTypeInstance();
        JavaRefTypeInstance fieldRefType = (JavaRefTypeInstance) fieldType.getDeGenerifiedType();
        String name = fieldRefType.getRawShortName();
        ClassFileField classFileField = fieldVariable.getClassFileField();
        classFileField.overrideName(name + ".this");
        classFileField.markSyntheticOuterRef();
        /*
         * TODO :
         * This is a bit of a hack - we may be referring to a classfile field from a partially analysed
         * class.  So replace the local one with the field variable one.
         */
        try {
            ClassFileField localClassFileField = classFile.getFieldByName(originalName);
            localClassFileField.overrideName(name + ".this");
            localClassFileField.markSyntheticOuterRef();
        } catch (NoSuchFieldException e) {
        }
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
        if (options.removeInnerClassSynthetics()) {

            /*
             * All constructors of inner classes should have their first argument removed,
             * and it should be marked as hidden.
             */
            if (classFile.isInnerClass()) {
                fixInnerClassConstructors(classFile);
            }

            replaceNestedSyntheticOuterRefs(classFile);

            inlineAccessors(state, classFile);
        }

        if (options.getBooleanOpt(OptionsImpl.REMOVE_DEAD_METHODS)) {
            removeDeadMethods(classFile);
        }

    }
}