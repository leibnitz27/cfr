package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.bytestream.ByteData;

public class OperationFactoryInvokeInterface extends OperationFactoryDefault {
    //private static final int LENGTH_OF_METHOD_INDEX = 2;
    private static final int OFFSET_OF_METHOD_INDEX = 1;
    private static final int LENGTH_OF_DATA = 4;

    OperationFactoryInvokeInterface() {
    }

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        byte[] args = bd.getBytesAt(LENGTH_OF_DATA, 1);
        ConstantPoolEntry[] cpEntries = new ConstantPoolEntry[]{cp.getEntry(bd.getU2At(OFFSET_OF_METHOD_INDEX))};
        return new Op01WithProcessedDataAndByteJumps(instr, args, null, offset, cpEntries);
    }

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPoolEntry[] cpEntries,
                                    StackSim stackSim, Method method) {
        ConstantPoolEntryMethodRef methodRef = (ConstantPoolEntryMethodRef) cpEntries[0];

        return methodRef.getNameAndTypeEntry().getStackDelta(true);
    }
}
