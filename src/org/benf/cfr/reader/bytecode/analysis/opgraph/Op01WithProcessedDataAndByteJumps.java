package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.*;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 28/04/2011
 * Time: 07:21
 * To change this template use File | Settings | File Templates.
 */

public class Op01WithProcessedDataAndByteJumps {
    private final JVMInstr instruction;
    /* For 0 argument opcodes, the below should be irrelevant, indeed we could have singletons. */
    // Raw arguments after this opcode
    private final byte[] data;
    private final int[] rawTargetOffsets;
    private final ConstantPoolEntry[] constantPoolEntries;
    private final int originalRawOffset;

    public Op01WithProcessedDataAndByteJumps(JVMInstr instruction, byte[] data, int[] rawTargetOffsets, int originalRawOffset)
    {
        this.instruction = instruction;
        this.data = data;
        this.rawTargetOffsets = rawTargetOffsets;
        this.constantPoolEntries = null;
        this.originalRawOffset = originalRawOffset;
    }

    public Op01WithProcessedDataAndByteJumps(JVMInstr instruction, byte[] data, int[] rawTargetOffsets, int originalRawOffset, ConstantPoolEntry[] constantPoolEntries) {
        this.instruction = instruction;
        this.data = data;
        this.rawTargetOffsets = rawTargetOffsets;
        this.originalRawOffset = originalRawOffset;
        this.constantPoolEntries = constantPoolEntries;
    }

    public StackDelta getStackDelta(ConstantPool cp)
    {
        return instruction.getStackDelta(data, cp, constantPoolEntries);
    }

    public JVMInstr getJVMInstr()
    {
        return instruction;
    }

    public byte[] getData()
    {
        return data;
    }

    public Op02WithProcessedDataAndRefs createOp2(ConstantPool cp, int index) {
        return new Op02WithProcessedDataAndRefs(instruction, data, index, cp, constantPoolEntries, originalRawOffset);
    }

    public int[] getAbsoluteIndexJumps(int thisOpByteIndex, Map<Integer, Integer> lutByOffset)
    {
        int thisOpInstructionIndex = lutByOffset.get(thisOpByteIndex);
        if (rawTargetOffsets == null)
        {
            return new int[]{thisOpInstructionIndex+1};
        }
        // Otherwise, figure out what the relative byte offsets we have are as instruction offsets,
        // and create a branching indexed operation.

        int targetIndexes[] = new int[rawTargetOffsets.length];
        for (int x=0;x<rawTargetOffsets.length;++x)
        {
            int targetRawAddress = thisOpByteIndex + rawTargetOffsets[x];
            int targetIndex = lutByOffset.get(targetRawAddress);
            targetIndexes[x] = targetIndex;
        }
        return targetIndexes;
    }

    public int getInstructionLength()
    {
        return data == null ? 1 : (data.length + 1);
    }

    @Override
    public String toString() {
        return "op1 : " + instruction + ", length " + getInstructionLength();
    }
}
