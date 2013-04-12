package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryInvokeDynamic;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 */
public class OperationFactoryInvokeDynamic extends OperationFactoryDefault {
    private static final int LENGTH_OF_FIELD_INDEX = 2;

    public OperationFactoryInvokeDynamic() {
    }

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        byte[] args = bd.getBytesAt(LENGTH_OF_FIELD_INDEX, 1);
        int[] targetOffsets = null; // we know the nextr instr, it's our successor (after the invoke returns).
        ConstantPoolEntry[] cpEntries = new ConstantPoolEntry[]{cp.getEntry(bd.getS2At(1))};

        return new Op01WithProcessedDataAndByteJumps(instr, args, targetOffsets, offset, cpEntries);
    }

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPool cp, ConstantPoolEntry[] cpEntries, StackSim stackSim) {
        ConstantPoolEntryInvokeDynamic invokeDynamic = (ConstantPoolEntryInvokeDynamic) cpEntries[0];

        //return cp.getNameAndTypeEntry(methodRef.getNameAndTypeIndex()).getStackDelta(true, cp);
        throw new UnsupportedOperationException("Can't decode INVOKE_DYNAMIC yet");
    }
}
