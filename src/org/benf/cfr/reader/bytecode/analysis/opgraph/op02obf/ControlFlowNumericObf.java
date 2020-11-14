package org.benf.cfr.reader.bytecode.analysis.opgraph.op02obf;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.Method;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/*
 * This covers two cases:
 *
 * 1: Java integers just wrap around, so obfuscating heedlessly with
 * i += 2  --> i += 100 ; i -= 98;
 * is perfectly safe.
 * It's not much of an obfuscation, but worth undoing.
 *
 * 2: Its common to use simple math operations to hide constants
 * i += 2 --> i += ((241 ^ 147) + 2) / 50
 */
public class ControlFlowNumericObf {
    public static ControlFlowNumericObf Instance = new ControlFlowNumericObf();

    public void process(Method method, List<Op02WithProcessedDataAndRefs> op2list) {
        for (int idx = 0; idx < op2list.size(); ++idx) {
            Op02WithProcessedDataAndRefs op = op2list.get(idx);
            JVMInstr instr = op.getInstr();
            if (instr == JVMInstr.IINC) {
                processOne(op, op2list, idx);
            } else if (isIntMath(instr)) {
                processTwo(op, op2list, idx);
            }
        }
    }

    private void processOne(Op02WithProcessedDataAndRefs op, List<Op02WithProcessedDataAndRefs> op2list, int idx) {
        Op02WithProcessedDataAndRefs next = op.getTargets().get(0);
        if (next != op2list.get(idx+1)) return;
        if (next.getSources().size() != 1) return;
        if (next.getInstr() != JVMInstr.IINC) return;
        Pair<Integer, Integer> iinc1 = op.getIincInfo();
        Pair<Integer, Integer> iinc2 = next.getIincInfo();
        if (!iinc1.getFirst().equals(iinc2.getFirst())) return;
        int diff = iinc1.getSecond() + iinc2.getSecond();
        if ((byte)diff == diff) {
            // don't mutate the data, that's shared between passes - create new.
            Op02WithProcessedDataAndRefs replace = new Op02WithProcessedDataAndRefs(
                    JVMInstr.IINC,
                    new byte[]{(byte)(int)iinc1.getFirst(), (byte)diff},
                    op.getIndex(),
                    op.getCp(),
                    null,
                    op.getOriginalRawOffset(),
                    op.getBytecodeLoc()
            );
            op2list.set(idx, replace);
            Op02WithProcessedDataAndRefs.replace(op, replace);
            next.nop();
        }
    }

    private void processTwo(Op02WithProcessedDataAndRefs op, List<Op02WithProcessedDataAndRefs> op2list, int idx) {
        Op02WithProcessedDataAndRefs prev1 = op.getSources().get(0);
        Op02WithProcessedDataAndRefs prev2 = prev1.getSources().get(0);
        if (!isIntConstant(prev1.getInstr())) return;
        if (!isIntConstant(prev2.getInstr())) return;
        int v1 = getConstValue(prev1);
        int v2 = getConstValue(prev2);
        int value = doMath(op.getInstr(), v1, v2);
        // don't mutate the data, that's shared between passes - create new.
        Op02WithProcessedDataAndRefs replace;
        if (value > Short.MAX_VALUE || value < Short.MIN_VALUE) {
            replace = new Op02WithProcessedDataAndRefs(
                    JVMInstr.LDC,
                    new byte[]{
                            (byte) (value >>> 24 & 0xFF),
                            (byte) (value >>> 16 & 0xFF),
                            (byte) (value >>> 8 & 0xFF),
                            (byte) (value & 0xFF)},
                    op.getIndex(),
                    op.getCp(),
                    null,
                    op.getOriginalRawOffset(),
                    op.getBytecodeLoc()
            );
        } else {
            replace = new Op02WithProcessedDataAndRefs(
                    JVMInstr.SIPUSH,
                    new byte[]{
                            (byte) (value >>> 8 & 0xFF),
                            (byte) (value & 0xFF)},
                    op.getIndex(),
                    op.getCp(),
                    null,
                    op.getOriginalRawOffset(),
                    op.getBytecodeLoc()
            );
        }

        op2list.set(idx, replace);
        Op02WithProcessedDataAndRefs.replace(op, replace);
        prev1.nop();
        prev2.nop();
    }

    private int doMath(JVMInstr instr, int arg1, int arg2) {
        switch (instr) {
            case IXOR:
                return arg2 ^ arg1;
            case IOR:
                return arg2 | arg1;
            case IAND:
                return arg2 & arg1;
            case IADD:
                return arg2 + arg1;
            case ISUB:
                return arg2 - arg1;
            case IMUL:
                return arg2 * arg1;
            case IDIV:
                return arg2 / arg1;
            case ISHL:
                return arg2 << arg1;
            case ISHR:
                return arg2 >> arg1;
            default:
                throw new IllegalStateException();
        }
    }

    private int getConstValue(Op02WithProcessedDataAndRefs op) {
        JVMInstr instr = op.getInstr();
        if (instr == JVMInstr.BIPUSH) {
            return op.getInstrArgByte(0);
        } else if (instr == JVMInstr.ICONST_M1) {
            return -1;
        } else if (instr == JVMInstr.ICONST_0) {
            return 0;
        } else if (instr == JVMInstr.ICONST_1) {
            return 1;
        } else if (instr == JVMInstr.ICONST_2) {
            return 2;
        } else if (instr == JVMInstr.ICONST_3) {
            return 3;
        } else if (instr == JVMInstr.ICONST_4) {
            return 4;
        } else if (instr == JVMInstr.ICONST_5) {
            return 5;
        }
        return op.getInstrArgShort(0);
    }

    private boolean isIntConstant(JVMInstr instr) {
        return instr == JVMInstr.SIPUSH || instr == JVMInstr.BIPUSH || instr == JVMInstr.LDC;
    }

    private boolean isIntMath(JVMInstr instr) {
        return instr == JVMInstr.IXOR || instr == JVMInstr.IOR || instr == JVMInstr.IAND
                || instr == JVMInstr.IADD || instr == JVMInstr.ISUB || instr == JVMInstr.IMUL
                || instr == JVMInstr.IDIV || instr == JVMInstr.ISHL || instr == JVMInstr.ISHR;
    }
}
