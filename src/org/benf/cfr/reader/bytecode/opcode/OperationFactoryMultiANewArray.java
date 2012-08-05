package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryMultiANewArray extends OperationFactoryDefault {
    private static final int LENGTH_OF_DATA = 3;
    public static final int OFFSET_OF_DIMS = 2;

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        byte[] args = bd.getBytesAt(LENGTH_OF_DATA, 1);
        int[] targetOffsets = null; // we know the nextr instr, it's our successor (after the invoke returns).
        ConstantPoolEntry[] cpEntries = new ConstantPoolEntry[]{cp.getEntry(bd.getS2At(1))};

        return new Op01WithProcessedDataAndByteJumps(instr, args, targetOffsets, offset, cpEntries);
    }

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPool cp, ConstantPoolEntry[] cpEntries, StackSim stackSim) {
        short numDims = data[OFFSET_OF_DIMS];
        if (numDims < 0) {
            throw new ConfusedCFRException("NYI : Unsupported num of dims, should be using a short not a byte.");
        }

        List<StackType> stackTypeList = ListFactory.newList();
        for (int x = 0; x < numDims; ++x) {
            stackTypeList.add(StackType.INT);
        }
        return new StackDelta(new StackTypes(stackTypeList), StackType.REF.asList());
    }
}
