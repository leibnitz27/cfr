package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.EnumClassRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.StaticLifter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.getopt.CFRState;

import java.util.Iterator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/05/2013
 * Time: 06:22
 * <p/>
 * Analysis which needs to be performed on the whole classfile in one go, once we've
 * performed other basic code analysis.
 */
public class CodeAnalyserWholeClass {
    public static void wholeClassAnalysis(ClassFile classFile, CFRState state) {
        /*
         * Whole class analysis / transformation - i.e. if it's an enum class, we will need to rewrite
         * several methods.
         */
        EnumClassRewriter.rewriteEnumClass(classFile, state);

        /*
         * All constructors of inner classes should have their first argument removed,
         * and it should be marked as hidden.
         */
        if (classFile.isInnerClass()) {
            fixInnerClassConstructors(classFile, state);
        }

        liftStaticInitialisers(classFile, state);
    }

    private static void fixInnerClassConstructors(ClassFile classFile, CFRState state) {
        if (classFile.testAccessFlag(AccessFlag.ACC_STATIC)) return;

        for (Method method : classFile.getConstructors()) {
            Op04StructuredStatement.fixInnerClassConstruction(state, method, method.getAnalysis());
        }
    }

    /* As much as possible, lift code from a <clinit> method into the declarations.
     * Because we can put arbitrary code in a clinit, this isn't always possible, however
     * we want to try because
     * a) it looks tidier!
     * b) interfaces MAY have static initialisers, but MAY NOT have clinit methods.
     *    (in java 1.7)
     */
    private static void liftStaticInitialisers(ClassFile classFile, CFRState state) {
        Method staticInit;
        try {
            staticInit = classFile.getMethodByName(MiscConstants.STATIC_INIT_METHOD);
        } catch (NoSuchMethodException e) {
            return;
        }

        new StaticLifter(classFile).liftStatics(staticInit);
    }
}
