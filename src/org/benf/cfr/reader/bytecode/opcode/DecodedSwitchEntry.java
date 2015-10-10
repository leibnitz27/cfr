package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.util.StringUtils;

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("case ");
        for (Integer val : value) {
            first = StringUtils.comma(first, sb);
            sb.append(val == null ? "default" : val);
        }
        sb.append(" -> ").append(bytecodeTarget);
        return sb.toString();
    }
}
