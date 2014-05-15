package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.List;

public class OperationFactoryTableSwitch extends OperationFactoryDefault {

    // offsets relative to computed start of default
    private static final int OFFSET_OF_LOWBYTE = 4;
    private static final int OFFSET_OF_HIGHBYTE = 8;
    private static final int OFFSET_OF_OFFSETS = 12;


    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        int curoffset = offset + 1;
        // We need to align the next byte to a 4 byte boundary relative to the start of the method.
        int overflow = (curoffset % 4);
        overflow = overflow > 0 ? 4 - overflow : 0;
        int startdata = 1 + overflow;
        int lowvalue = bd.getS4At(startdata + OFFSET_OF_LOWBYTE);
        int highvalue = bd.getS4At(startdata + OFFSET_OF_HIGHBYTE);
        int numoffsets = highvalue - lowvalue + 1;
        int size = overflow + OFFSET_OF_OFFSETS + 4 * numoffsets;
        byte[] rawData = bd.getBytesAt(size, 1);

        DecodedSwitch dts = new DecodedTableSwitch(rawData, offset);
        int defaultTarget = dts.getDefaultTarget();
        List<DecodedSwitchEntry> targets = dts.getJumpTargets();
        int[] targetOffsets = new int[targets.size() + 1];
        targetOffsets[0] = defaultTarget;
        int out = 1;
        for (DecodedSwitchEntry target : targets) {
            targetOffsets[out++] = target.getBytecodeTarget();
        }

        return new Op01WithProcessedDataAndByteJumps(instr, rawData, targetOffsets, offset);
    }

}
