package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.entities.AbstractConstantPoolEntry;
import org.benf.cfr.reader.entities.bootstrap.MethodHandleBehaviour;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class ConstantPoolEntryMethodHandle extends AbstractConstantPoolEntry {
    private static final long OFFSET_OF_REFERENCE_KIND = 1;
    private static final long OFFSET_OF_REFERENCE_INDEX = 2;

    private final MethodHandleBehaviour referenceKind;
    private final int referenceIndex;

    public ConstantPoolEntryMethodHandle(ConstantPool cp, ByteData data) {
        super(cp);
        this.referenceKind = MethodHandleBehaviour.decode(data.getS1At(OFFSET_OF_REFERENCE_KIND));
        this.referenceIndex = data.getU2At(OFFSET_OF_REFERENCE_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 4;
    }

    @Override
    public void dump(Dumper d) {
        d.print(this.toString());
    }

    public MethodHandleBehaviour getReferenceKind() {
        return referenceKind;
    }

    public ConstantPoolEntryMethodRef getMethodRef() {
        return getCp().getMethodRefEntry(referenceIndex);
    }

    public ConstantPoolEntryFieldRef getFieldRef() {
        return getCp().getFieldRefEntry(referenceIndex);
    }

    public boolean isFieldRef() {
        switch (referenceKind) {
            case GET_FIELD:
            case GET_STATIC:
            case PUT_FIELD:
            case PUT_STATIC:
                return true;
            default:
                return false;
        }
    }

    public String getLiteralName() {
        if (isFieldRef()) {
            return QuotingUtils.enquoteString(getFieldRef().getLocalName());
        } else {
            return getMethodRef().getMethodPrototype().toString();
        }
    }

    @Override
    public String toString() {
        return "MethodHandle value=" + referenceKind + "," + referenceIndex;
    }
}
