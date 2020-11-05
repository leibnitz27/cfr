package org.benf.cfr.reader.bytecode.analysis.opgraph.op02obf;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;
import java.util.SortedMap;

public class ControlFlowNullException extends SimpleControlFlowBase {
    public static ControlFlowNullException Instance = new ControlFlowNullException();

    @Override
    protected boolean checkTry(List<Op02WithProcessedDataAndRefs> op2list, int from, int to, Op02WithProcessedDataAndRefs handlerJmp) {
        Op02WithProcessedDataAndRefs start = op2list.get(from);
        if (start.getInstr() != JVMInstr.INVOKEVIRTUAL) return false;
        Op02WithProcessedDataAndRefs tgt = start.getTargets().get(0);
        if (tgt.getInstr() != JVMInstr.POP) return false;

        ConstantPoolEntryMethodRef function = (ConstantPoolEntryMethodRef)start.getCpEntries()[0];
        MethodPrototype mp = function.getMethodPrototype();
        if (mp.getClassType() != TypeConstants.OBJECT) return false;
        if (!mp.getName().equals(MiscConstants.GET_CLASS_NAME)) return false;
        start.replaceInstr(JVMInstr.IFNULL);

        tgt.nop();
        start.addTarget(handlerJmp);
        handlerJmp.addSource(start);
        return true;
    }

    @Override
    protected Op02WithProcessedDataAndRefs checkHandler(List<Op02WithProcessedDataAndRefs> op2list, int idx) {
        return getLastTargetIf(op2list, idx, JVMInstr.POP, JVMInstr.GOTO);
    }
}
