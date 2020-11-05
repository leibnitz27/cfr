package org.benf.cfr.reader.bytecode.analysis.opgraph.op02obf;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;
import java.util.SortedMap;

public class ControlFlowExceptions {
    /*
     * Undo a very simple control flow obfuscation where integer division by 0 is used with an exception handler.
     */
    public void process(Method method, ExceptionAggregator exceptions, List<Op02WithProcessedDataAndRefs> op2list, SortedMap<Integer, Integer> lutByOffset) {
        List<ExceptionGroup> groups = ListFactory.newList(exceptions.getExceptionsGroups());
        nextGroup : for (ExceptionGroup group : groups) {
            Op02WithProcessedDataAndRefs handlerJmp = checkHandler(group, op2list, lutByOffset);
            if (handlerJmp == null) continue;
            Integer from = lutByOffset.get(group.getBytecodeIndexFrom());
            Integer to = lutByOffset.get(group.getBytecodeIndexTo());
            if (from == null || to == null) continue;
            if (from >= op2list.size() || to >= op2list.size()) continue;
            Op02WithProcessedDataAndRefs tgt = getLastTargetIf(op2list, from, JVMInstr.DUP, JVMInstr.IDIV, JVMInstr.POP);
            if (tgt == null) continue;
            for (int x = from + 3; x < to; ++x) {
                if (!op2list.get(x).getInstr().isNoThrow()) continue nextGroup;
            }
            op2list.get(from).replaceInstr(JVMInstr.NOP);
            op2list.get(from+1).replaceInstr(JVMInstr.ICONST_0);
            Op02WithProcessedDataAndRefs op2 = op2list.get(from + 2);
            op2.replaceInstr(JVMInstr.IF_ICMPEQ);
            op2.getTargets().add(handlerJmp);
            handlerJmp.getSources().add(op2);
            exceptions.getExceptionsGroups().remove(group);
        }
    }

    private Op02WithProcessedDataAndRefs checkHandler(ExceptionGroup group, List<Op02WithProcessedDataAndRefs> op2list, SortedMap<Integer, Integer> lutByOffset) {
        List<ExceptionGroup.Entry> entries = group.getEntries();
        if (entries.size() != 1) return null;
        int handler = entries.get(0).getBytecodeIndexHandler();
        Integer tgtIdx = lutByOffset.get(handler);
        if (tgtIdx == null) return null;
        // For now, check a very very specific pattern ;)
        return getLastTargetIf(op2list, tgtIdx, JVMInstr.POP, JVMInstr.GOTO);
    }

    private Op02WithProcessedDataAndRefs getLastTargetIf(List<Op02WithProcessedDataAndRefs> op2list, Integer start, JVMInstr ... instrs) {
        if (start + instrs.length > op2list.size()) return null;
        for (int x = 0;x<instrs.length;++x) {
            Op02WithProcessedDataAndRefs instr = op2list.get(start + x);
            if (x > 0 && instr.getSources().size() != 1 && instr.getSources().get(0) != op2list.get(start + x - 1)) return null;
            if (instr.getInstr() != instrs[x]) return null;
        }
        return op2list.get(start + instrs.length -1).getTargets().get(0);
    }
}
