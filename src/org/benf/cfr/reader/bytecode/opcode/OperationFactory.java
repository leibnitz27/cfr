package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:12
 * To change this template use File | Settings | File Templates.
 */
public interface OperationFactory {
    /*
     * When an operation is being decoded, it may need to know about the method / class it's in
     * (eg invokedynamic);
     */
    StackDelta getStackDelta(JVMInstr instr, byte[] data, ConstantPoolEntry[] cpEntries,
                             StackSim stackSim, Method method);

    Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset);
}
