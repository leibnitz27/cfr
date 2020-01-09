package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.util.UnverifiableJumpException;

import java.util.Map;

public class Op01WithProcessedDataAndByteJumps {
    private final JVMInstr instruction;
    /* For 0 argument opcodes, the below should be irrelevant, indeed we could have singletons. */
    // Raw arguments after this opcode
    private final byte[] data;

    private final int[] rawTargetOffsets;
    private final ConstantPoolEntry[] constantPoolEntries;
    private final int originalRawOffset;

    public Op01WithProcessedDataAndByteJumps(JVMInstr instruction, byte[] data, int[] rawTargetOffsets, int originalRawOffset) {
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

    public JVMInstr getJVMInstr() {
        return instruction;
    }

    public int[] getRawTargetOffsets() {
        return rawTargetOffsets;
    }

    public int getOriginalRawOffset() {
        return originalRawOffset;
    }

    public byte[] getData() {
        return data;
    }

    public Op02WithProcessedDataAndRefs createOp2(ConstantPool cp, int index) {
        return new Op02WithProcessedDataAndRefs(instruction, data, index, cp, constantPoolEntries, originalRawOffset);
    }

    public int[] getAbsoluteIndexJumps(int thisOpByteIndex, Map<Integer, Integer> lutByOffset) {
        int thisOpInstructionIndex = lutByOffset.get(thisOpByteIndex);
        if (rawTargetOffsets == null) {
            return new int[]{thisOpInstructionIndex + 1};
        }
        // Otherwise, figure out what the relative byte offsets we have are as instruction offsets,
        // and create a branching indexed operation.

        int[] targetIndexes = new int[rawTargetOffsets.length];
        for (int x = 0; x < rawTargetOffsets.length; ++x) {
            int targetRawAddress = thisOpByteIndex + rawTargetOffsets[x];
            Integer targetIndex = lutByOffset.get(targetRawAddress);
            if (targetIndex == null) {
                // Oh this is fun.  We have a jump-to-middle of instruction.
                // (https://anthony.som.codes/blog/2019-12-30-jvm-hackery-noverify/)
                throw new UnverifiableJumpException();
            }
            targetIndexes[x] = targetIndex;
        }
        return targetIndexes;
    }

    public int getInstructionLength() {
        return data == null ? 1 : (data.length + 1);
    }

    @Override
    public String toString() {
        return "op1 : " + instruction + ", length " + getInstructionLength();
    }

    public Integer getAStoreIdx() {
        switch (instruction) {
            case ASTORE:
                return (int) data[0];
            case ASTORE_WIDE:
                throw new UnsupportedOperationException();
            case ASTORE_0:
                return 0;
            case ASTORE_1:
                return 1;
            case ASTORE_2:
                return 2;
            case ASTORE_3:
                return 3;
        }
        return null;
    }

    public Integer getALoadIdx() {
        switch (instruction) {
            case ALOAD:
                return (int) data[0];
            case ALOAD_WIDE:
                throw new UnsupportedOperationException();
            case ALOAD_0:
                return 0;
            case ALOAD_1:
                return 1;
            case ALOAD_2:
                return 2;
            case ALOAD_3:
                return 3;
        }
        return null;
    }

}
