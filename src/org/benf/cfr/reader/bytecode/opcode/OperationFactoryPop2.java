package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDeltaImpl;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryPop2 extends OperationFactoryDefault {

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPool cp, ConstantPoolEntry[] cpEntries,
                                    StackSim stackSim, Method method) {
        StackType topStackEntry = stackSim.getEntry(0).getType();
        if (topStackEntry.getComputationCategory() == 2) {
            return new StackDeltaImpl(topStackEntry.asList(), StackTypes.EMPTY);
        } else {
            StackType nextStackEntry = stackSim.getEntry(1).getType();
            StackTypes stackTypesPopped = new StackTypes(topStackEntry, nextStackEntry);
            return new StackDeltaImpl(stackTypesPopped, StackTypes.EMPTY);
        }
    }

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        byte[] args = null;
        int[] targetOffsets = null; // we know the nextr instr, it's our successor.
        return new Op01WithProcessedDataAndByteJumps(instr, args, targetOffsets, offset);
    }
}
