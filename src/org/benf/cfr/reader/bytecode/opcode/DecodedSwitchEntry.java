package org.benf.cfr.reader.bytecode.opcode;

import java.util.List;

public class DecodedSwitchEntry {
    private final List<Integer> value;
    private final int bytecodeTarget;

    public DecodedSwitchEntry(List<Integer> value, int bytecodeTarget) {
        this.bytecodeTarget = bytecodeTarget;
        this.value = value;
    }

    public List<Integer> getValue() {
        return value;
    }

    public int getBytecodeTarget() {
        return bytecodeTarget;
    }
}
