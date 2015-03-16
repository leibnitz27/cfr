package org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ClassFile;

public interface GetClassTest {
    JVMInstr getInstr();

    boolean test(ClassFile classFile, Op02WithProcessedDataAndRefs item);
}
