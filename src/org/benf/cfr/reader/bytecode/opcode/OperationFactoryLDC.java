package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.stack.StackType;
import org.benf.cfr.reader.bytecode.analysis.stack.StackTypes;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryLiteral;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryLDC extends OperationFactoryCPEntry {

    @Override
    public StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPool cp, ConstantPoolEntry[] cpEntries, StackSim stackSim) {
        ConstantPoolEntryLiteral constantPoolEntryLiteral = (ConstantPoolEntryLiteral) cpEntries[0];
        if (constantPoolEntryLiteral == null) {
            throw new ConfusedCFRException("Expecting ConstantPoolEntryLiteral");
        }
        StackType stackType = constantPoolEntryLiteral.getStackType();


        return new StackDelta(StackTypes.EMPTY, stackType.asList());
    }

}
