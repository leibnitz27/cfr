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
public class OperationFactoryDefault implements OperationFactory {

    public enum Handler {
        INSTANCE(new OperationFactoryDefault());

        private final OperationFactoryDefault h;

        Handler(OperationFactoryDefault h) {
            this.h = h;
        }

        public OperationFactory getHandler() {
            return h;
        }
    }

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPool cp, ConstantPoolEntry[] cpEntries,
                                    StackSim stackSim, Method method) {
        return new StackDeltaImpl(instr.getRawStackPopped(), instr.getRawStackPushed());
    }

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        byte[] args = instr.getRawLength() == 0 ? null : bd.getBytesAt(instr.getRawLength(), 1);
        int[] targetOffsets = null; // we know the nextr instr, it's our successor.
        return new Op01WithProcessedDataAndByteJumps(instr, args, targetOffsets, offset);
    }

    /*
     * Misc helpers.
     */
    protected static StackTypes getStackTypes(StackSim stackSim, Integer... indexes) {
        if (indexes.length == 1) {
            return stackSim.getEntry(indexes[0]).getType().asList();
        } else {
            List<StackType> stackTypes = ListFactory.newList();
            for (Integer index : indexes) {
                stackTypes.add(stackSim.getEntry(index).getType());
            }
            return new StackTypes(stackTypes);
        }
    }

    protected static int getCat(StackSim stackSim, int index) {
        return stackSim.getEntry(index).getType().getComputationCategory();
    }

    protected static void checkCat(StackSim stackSim, int index, int category) {
        if (getCat(stackSim, index) != category) {
            throw new ConfusedCFRException("Expected category " + category + " at index " + index);
        }
    }

}
