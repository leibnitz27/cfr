package org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.types.DynamicInvokeType;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.bootstrap.BootstrapMethodInfo;
import org.benf.cfr.reader.entities.bootstrap.MethodHandleBehaviour;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.List;

public class Op02LambdaRewriter {

    private static Op02WithProcessedDataAndRefs getSinglePrev(Op02WithProcessedDataAndRefs item) {
        if (item.getSources().size() != 1) return null;
        Op02WithProcessedDataAndRefs prev = item.getSources().get(0);
        if (prev.getTargets().size() != 1) return null;
        return prev;
    }

    /*
     * This is all a very cheap and dirty way of doing things - replace with a regex?
     */
    private static void tryRemove(ClassFile classFile, Op02WithProcessedDataAndRefs item) {

        Op02WithProcessedDataAndRefs pop = getSinglePrev(item);
        if (pop == null) return;
        if (!isLambda(classFile, item)) return;

        if (pop.getInstr() != JVMInstr.POP) return;
        Op02WithProcessedDataAndRefs getClass = getSinglePrev(pop);
        if (getClass == null) return;
        if (!isGetClass(getClass)) return;

        Op02WithProcessedDataAndRefs dup = getSinglePrev(getClass);
        if (dup == null) return;
        if (dup.getInstr() != JVMInstr.DUP) return;

        dup.nop();
        getClass.nop();
        pop.nop();
    }

    private static boolean isGetClass(Op02WithProcessedDataAndRefs item) {
        ConstantPoolEntry[] cpEntries = item.getCpEntries();
        ConstantPoolEntryMethodRef function = (ConstantPoolEntryMethodRef) cpEntries[0];

        MethodPrototype methodPrototype = function.getMethodPrototype();
        if (!methodPrototype.getName().equals(MiscConstants.GET_CLASS_NAME)) return false;
        if (methodPrototype.getArgs().size() != 0) return false;
        if (!methodPrototype.getReturnType().getDeGenerifiedType().getRawName().equals(TypeConstants.className))
            return false;
        return true;
    }

    private static boolean isLambda(ClassFile classFile, Op02WithProcessedDataAndRefs item) {

        ConstantPoolEntry[] cpEntries = item.getCpEntries();
        ConstantPoolEntryInvokeDynamic invokeDynamic = (ConstantPoolEntryInvokeDynamic) cpEntries[0];

        // Should have this as a member on name and type
        int idx = invokeDynamic.getBootstrapMethodAttrIndex();

        BootstrapMethodInfo bootstrapMethodInfo = classFile.getBootstrapMethods().getBootStrapMethodInfo(idx);
        ConstantPoolEntryMethodRef methodRef = bootstrapMethodInfo.getConstantPoolEntryMethodRef();
        String methodName = methodRef.getName();

        DynamicInvokeType dynamicInvokeType = DynamicInvokeType.lookup(methodName);
        if (dynamicInvokeType == DynamicInvokeType.UNKNOWN) return false;

        return true;
    }

    public static void removeInvokeGetClass(ClassFile classFile, List<Op02WithProcessedDataAndRefs> op02list) {
        for (Op02WithProcessedDataAndRefs item : op02list) {
            if (item.getInstr() == JVMInstr.INVOKEDYNAMIC) {
                tryRemove(classFile, item);
            }
        }
    }
}
