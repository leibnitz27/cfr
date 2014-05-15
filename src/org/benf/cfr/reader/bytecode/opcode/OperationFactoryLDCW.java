package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDeltaImpl;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryLiteral;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ConfusedCFRException;

public class OperationFactoryLDCW extends OperationFactoryCPEntryW {

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPoolEntry[] cpEntries,
                                    StackSim stackSim, Method method) {
        ConstantPoolEntryLiteral constantPoolEntryLiteral = (ConstantPoolEntryLiteral) cpEntries[0];
        if (constantPoolEntryLiteral == null) {
            throw new ConfusedCFRException("Expecting ConstantPoolEntryLiteral");
        }
        StackType stackType = constantPoolEntryLiteral.getStackType();
        int requiredComputationCategory = getRequiredComputationCategory();
        if (stackType.getComputationCategory() != requiredComputationCategory) {
            throw new ConfusedCFRException("Got a literal, but expected a different category");
        }

        return new StackDeltaImpl(StackTypes.EMPTY, stackType.asList());
    }

    protected int getRequiredComputationCategory() {
        return 1;
    }

}
