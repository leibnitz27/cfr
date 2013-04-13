package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.entities.bootstrap.MethodHandleBehaviour;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryMethodHandle implements ConstantPoolEntry {
    private static final long OFFSET_OF_REFERENCE_KIND = 1;
    private static final long OFFSET_OF_REFERENCE_INDEX = 2;

    private final MethodHandleBehaviour referenceKind;
    private final short referenceIndex;

    public ConstantPoolEntryMethodHandle(ByteData data) {
        this.referenceKind = MethodHandleBehaviour.decode(data.getS1At(OFFSET_OF_REFERENCE_KIND));
        this.referenceIndex = data.getS2At(OFFSET_OF_REFERENCE_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 4;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print(this.toString());
    }

    public MethodHandleBehaviour getReferenceKind() {
        return referenceKind;
    }

    public short getReferenceIndex() {
        return referenceIndex;
    }

    @Override
    public String toString() {
        return "MethodHandle value=" + referenceKind + "," + referenceIndex;
    }
}
