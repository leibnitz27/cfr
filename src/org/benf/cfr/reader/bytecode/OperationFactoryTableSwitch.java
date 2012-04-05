package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:18
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryTableSwitch extends OperationFactoryDefault {

    // offsets relative to computed start of default
    private static final int OFFSET_OF_DEFAULT = 0;
    private static final int OFFSET_OF_LOWBYTE = 4;
    private static final int OFFSET_OF_HIGHBYTE = 8;
    private static final int OFFSET_OF_OFFSETS = 12;


    @Override
    public Op01WithProcessedDataAndByteJumps createOperation(JVMInstr instr, ByteData bd, ConstantPool cp, int offset) {
        int curoffset = offset + 1;
        // We need to align the next byte to a 4 byte boundary relative to the start of the method.
        int overflow = (curoffset % 4);
        overflow = overflow > 0 ? 4 - overflow : 0;
        int startdata = 1 + overflow;
        int defaultvalue = bd.getU4At(startdata + OFFSET_OF_DEFAULT);
        int lowvalue = bd.getU4At(startdata + OFFSET_OF_LOWBYTE);
        int highvalue = bd.getU4At(startdata + OFFSET_OF_HIGHBYTE);
        int numoffsets = highvalue - lowvalue + 1;
        for (int x = 0; x < numoffsets; ++x) {
            int jumpfor = bd.getU4At(startdata + OFFSET_OF_OFFSETS + 4 * x);
            System.out.println("Jump for " + (lowvalue + x) + " = " + jumpfor);
        }


        throw new NotImplementedException();
    }

}
