package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryGotoW extends OperationFactoryDefault {

    private static long OFFSET_OF_TARGET = 1;

    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        byte[] args = bd.getBytesAt(instr.getRawLength(), 1);

        int targetOffset = bd.getS4At(OFFSET_OF_TARGET);

        int[] targetOffsets = new int[1];
        targetOffsets[0] = targetOffset;

        return new Op01WithProcessedDataAndByteJumps(instr, args, targetOffsets, offset);
    }
}
