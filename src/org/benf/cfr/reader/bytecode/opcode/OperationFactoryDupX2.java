package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDeltaImpl;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.bytestream.ByteData;

public class OperationFactoryDupX2 extends OperationFactoryDupBase {

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPoolEntry[] cpEntries,
                                    StackSim stackSim, Method method) {
        if (getCat(stackSim, 1) == 2) {
            checkCat(stackSim, 0, 1);
            return new StackDeltaImpl(
                    getStackTypes(stackSim, 0, 1),
                    getStackTypes(stackSim, 0, 1, 0)
            );
        } else {
            checkCat(stackSim, 0, 1);
            checkCat(stackSim, 2, 1);
            return new StackDeltaImpl(
                    getStackTypes(stackSim, 0, 1, 2),
                    getStackTypes(stackSim, 0, 1, 2, 0)
            );
        }
    }

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        return new Op01WithProcessedDataAndByteJumps(instr, null, null, offset);
    }
}
