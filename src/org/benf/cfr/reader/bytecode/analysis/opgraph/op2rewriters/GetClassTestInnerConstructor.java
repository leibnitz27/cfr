package org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StackValue;
import org.benf.cfr.reader.bytecode.analysis.types.DynamicInvokeType;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.bootstrap.BootstrapMethodInfo;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryInvokeDynamic;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.MiscConstants;

public class GetClassTestInnerConstructor implements GetClassTest {

    public static GetClassTest INSTANCE = new GetClassTestInnerConstructor();

    private GetClassTestInnerConstructor() {
    }

    @Override
    public JVMInstr getInstr() {
        return JVMInstr.INVOKESPECIAL;
    }

    @Override
    public boolean test(ClassFile classFile, Op02WithProcessedDataAndRefs item) {
        ConstantPoolEntryMethodRef function = (ConstantPoolEntryMethodRef) item.getCpEntries()[0];
        if (!function.getName().equals(MiscConstants.INIT_METHOD)) return false;
        JavaTypeInstance initType = function.getClassEntry().getTypeInstance();
        InnerClassInfo innerClassInfo = initType.getInnerClassHereInfo();
        if (!innerClassInfo.isInnerClass()) return false;
        return true;
    }
}
