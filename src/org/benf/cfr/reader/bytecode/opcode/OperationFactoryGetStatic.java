package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.stack.StackType;
import org.benf.cfr.reader.bytecode.analysis.stack.StackTypes;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryFieldRef;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * User: lee
 * Date: 21/04/2011
 */
public class OperationFactoryGetStatic extends OperationFactoryCPEntryW {

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPool cp, ConstantPoolEntry[] cpEntries, StackSim stackSim) {
        ConstantPoolEntryFieldRef fieldRef = (ConstantPoolEntryFieldRef) cpEntries[0];
        if (fieldRef == null) throw new ConfusedCFRException("Expecting fieldRef");
        StackType stackType = fieldRef.getStackType(cp);
        return new StackDelta(StackTypes.EMPTY, stackType.asList());
    }

}
