package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDeltaImpl;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryLiteral;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryLDC extends OperationFactoryCPEntry {

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPoolEntry[] cpEntries,
                                    StackSim stackSim, Method method) {
        ConstantPoolEntryLiteral constantPoolEntryLiteral = (ConstantPoolEntryLiteral) cpEntries[0];
        if (constantPoolEntryLiteral == null) {
            throw new ConfusedCFRException("Expecting ConstantPoolEntryLiteral");
        }
        StackType stackType = constantPoolEntryLiteral.getStackType();
        int requiredComputationCategory = 1;
        if (stackType.getComputationCategory() != requiredComputationCategory) {
            throw new ConfusedCFRException("Got a literal, but expected a different category");
        }


        return new StackDeltaImpl(StackTypes.EMPTY, stackType.asList());
    }

}
