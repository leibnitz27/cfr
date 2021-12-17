package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Set;

/*
 * non-sealed is an annoying beast - we need to check if any of our base/interfaces are sealed.
 * If they are, and we are NOT final or sealed, we are non-sealed, however this isn't explicit in
 * the classfile.
 */
public class SealedClassChecker {
    private static boolean isSealed(JavaTypeInstance t, DCCommonState state) {
        try {
            ClassFile i = state.getClassFile(t);
            return i.getAccessFlags().contains(AccessFlag.ACC_FAKE_SEALED);
        } catch (CannotLoadClassException e) {
            return false;
        }
    }

    private static boolean anySealed(ClassSignature sig, DCCommonState state) {
        if (isSealed(sig.getSuperClass(), state)) return true;
        for (JavaTypeInstance t : sig.getInterfaces()) {
            if (isSealed(t, state)) return true;
        }
        return false;
    }

    public static void rewrite(ClassFile classFile, DCCommonState state) {
        Set<AccessFlag> accessFlags = classFile.getAccessFlags();
        if (accessFlags.contains(AccessFlag.ACC_FAKE_SEALED)) {
            markExperimental(classFile, state);
            return;
        }
        if (accessFlags.contains(AccessFlag.ACC_FINAL)) {
            return;
        }
        if (anySealed(classFile.getClassSignature(), state)) {
            markExperimental(classFile, state);
            accessFlags.add(AccessFlag.ACC_FAKE_NON_SEALED);
        }
    }

    public static void markExperimental(ClassFile classFile, DCCommonState state) {
        if (!state.getOptions().optionIsSet(OptionsImpl.SEALED) &&
                OptionsImpl.sealedExpressionVersion.isExperimentalIn(classFile.getClassFileVersion())) {
            classFile.addComment(DecompilerComment.EXPERIMENTAL_FEATURE);
        }
    }
}
