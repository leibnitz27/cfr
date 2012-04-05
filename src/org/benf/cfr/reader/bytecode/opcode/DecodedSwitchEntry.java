package org.benf.cfr.reader.bytecode.opcode;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/04/2012
 */
public class DecodedSwitchEntry {
    private final int value;
    private final int bytecodeTarget;

    public DecodedSwitchEntry(int value, int bytecodeTarget) {
        this.bytecodeTarget = bytecodeTarget;
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public int getBytecodeTarget() {
        return bytecodeTarget;
    }
}
