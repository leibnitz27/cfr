package org.benf.cfr.reader.bytecode.analysis.opgraph.op02obf;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryInteger;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryLong;
import org.benf.cfr.reader.util.bytestream.BaseByteData;

import java.util.ArrayList;
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
        List<Op02WithProcessedDataAndRefs> toRemove = new ArrayList<Op02WithProcessedDataAndRefs>();
        Op02WithProcessedDataAndRefs prev1 = op.getSources().get(0);
        Op02WithProcessedDataAndRefs prev2 = prev1.getSources().get(0);
        toRemove.add(prev1);
        toRemove.add(prev2);
        while (isProxy(prev1)) {
            prev1 = prev1.getSources().get(0);
            prev2 = prev2.getSources().get(0);
            toRemove.add(prev2);
        }
        while (isProxy(prev2)) {
            prev2 = prev2.getSources().get(0);
            toRemove.add(prev2);
        }
        if (!isNumericConstant(prev1.getInstr())) return;
        if (!isNumericConstant(prev2.getInstr())) return;
        long v1 = getConstValue(prev1);
        long v2 = getConstValue(prev2);
        long value = doMath(op.getInstr(), v1, v2);
        // don't mutate the data, that's shared between passes - create new.
        Op02WithProcessedDataAndRefs replace;
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            int index = addLongToCp(op.getCp(), value);
            replace = new Op02WithProcessedDataAndRefs(
                    JVMInstr.LDC2_W,
                    new byte[]{
                            (byte)(index >>> 8 & 0xFF),
                            (byte)(index & 0xFF)},
                    op.getIndex(),
                    op.getCp(),
                    new ConstantPoolEntry[]{op.getCp().getEntry(index)},
                    op.getOriginalRawOffset(),
                    op.getBytecodeLoc()
            );
        } else if (value > Short.MAX_VALUE || value < Short.MIN_VALUE) {
            int index = addLongToCp(op.getCp(), value);
            replace = new Op02WithProcessedDataAndRefs(
                    JVMInstr.LDC,
                    new byte[]{
                            (byte)(index >>> 8 & 0xFF),
                            (byte)(index & 0xFF)},
                    op.getIndex(),
                    op.getCp(),
                    new ConstantPoolEntry[]{op.getCp().getEntry(index)},
                    op.getOriginalRawOffset(),
                    op.getBytecodeLoc()
            );
        } else {
            replace = new Op02WithProcessedDataAndRefs(
                    JVMInstr.SIPUSH,
                    new byte[]{
                            (byte)(value >>> 8 & 0xFF),
                            (byte)(value & 0xFF)},
                    op.getIndex(),
                    op.getCp(),
                    null,
                    op.getOriginalRawOffset(),
                    op.getBytecodeLoc()
            );
        }

        op2list.set(idx, replace);
        Op02WithProcessedDataAndRefs.replace(op, replace);
        for (Op02WithProcessedDataAndRefs removable : toRemove) {
            removable.nop();
        }
    }

    private int addLongToCp(ConstantPool cp, long value) {
        return cp.addEntry(new ConstantPoolEntryLong(cp, new BaseByteData(new byte[]{
                0,
                (byte)((int)(value >>> 56)),
                (byte)((int)(value >>> 48)),
                (byte)((int)(value >>> 40)),
                (byte)((int)(value >>> 32)),
                (byte)((int)(value >>> 24)),
                (byte)((int)(value >>> 16)),
                (byte)((int)(value >>> 8)),
                (byte)((int)(value)),
        })));
    }

    private boolean isProxy(Op02WithProcessedDataAndRefs op) {
        JVMInstr instr = op.getInstr();
        return instr == JVMInstr.I2L;
    }

    private long doMath(JVMInstr instr, long arg1, long arg2) {
        switch (instr) {
            case LXOR:
            case IXOR:
                return arg2 ^ arg1;
            case LOR:
            case IOR:
                return arg2 | arg1;
            case LAND:
            case IAND:
                return arg2 & arg1;
            case LADD:
            case IADD:
                return arg2 + arg1;
            case LSUB:
            case ISUB:
                return arg2 - arg1;
            case LMUL:
            case IMUL:
                return arg2 * arg1;
            case LDIV:
            case IDIV:
                return arg2 / arg1;
            case LSHL:
            case ISHL:
                return arg2 << arg1;
            case LSHR:
            case ISHR:
                return arg2 >> arg1;
            default:
                throw new IllegalStateException();
        }
    }

    private long getConstValue(Op02WithProcessedDataAndRefs op) {
        JVMInstr instr = op.getInstr();
        if (instr == JVMInstr.BIPUSH) {
            return op.getInstrArgByte(0);
        } else if (instr == JVMInstr.SIPUSH) {
            return op.getInstrArgShort(0);
        } else if (instr == JVMInstr.ICONST_M1) {
            return -1;
        } else if (instr == JVMInstr.ICONST_0 || instr == JVMInstr.LCONST_0) {
            return 0;
        } else if (instr == JVMInstr.ICONST_1 || instr == JVMInstr.LCONST_1) {
            return 1;
        } else if (instr == JVMInstr.ICONST_2) {
            return 2;
        } else if (instr == JVMInstr.ICONST_3) {
            return 3;
        } else if (instr == JVMInstr.ICONST_4) {
            return 4;
        } else if (instr == JVMInstr.ICONST_5) {
            return 5;
        } else if (instr == JVMInstr.LDC || instr == JVMInstr.LDC_W || instr == JVMInstr.LDC2_W) {
            ConstantPoolEntry value = op.getCpEntries()[0];
            if (value instanceof ConstantPoolEntryInteger) {
                return ((ConstantPoolEntryInteger) value).getValue();
            } else if (value instanceof ConstantPoolEntryLong) {
                return ((ConstantPoolEntryLong) value).getValue();
            }
        }
        throw new UnsupportedOperationException("Unsupported constant value holder");
    }

    /*
     * We do not have to check here for the contents of LDC since we are operating under the assumption
     * that they provide arguments for a math operation. Providing a non-numeric value would be illegal.
     */
    private boolean isNumericConstant(JVMInstr instr) {
        return instr == JVMInstr.SIPUSH || instr == JVMInstr.BIPUSH || instr == JVMInstr.ICONST_M1
                || instr == JVMInstr.ICONST_0 || instr == JVMInstr.ICONST_1 || instr == JVMInstr.ICONST_2
                || instr == JVMInstr.ICONST_3 || instr == JVMInstr.ICONST_4 || instr == JVMInstr.ICONST_5
                || instr == JVMInstr.LCONST_0 || instr == JVMInstr.LCONST_1 || instr == JVMInstr.LDC
                || instr == JVMInstr.LDC_W || instr == JVMInstr.LDC2_W;
    }

    private boolean isIntMath(JVMInstr instr) {
        return instr == JVMInstr.IXOR || instr == JVMInstr.IOR || instr == JVMInstr.IAND
                || instr == JVMInstr.IADD || instr == JVMInstr.ISUB || instr == JVMInstr.IMUL
                || instr == JVMInstr.IDIV || instr == JVMInstr.ISHL || instr == JVMInstr.ISHR
                || instr == JVMInstr.LXOR || instr == JVMInstr.LOR || instr == JVMInstr.LAND
                || instr == JVMInstr.LADD || instr == JVMInstr.LSUB || instr == JVMInstr.LMUL
                || instr == JVMInstr.LDIV || instr == JVMInstr.LSHL || instr == JVMInstr.LSHR;
    }
}
