package org.benf.cfr.reader.util.bytestream;

public class BaseByteData extends AbstractBackedByteData {
    public BaseByteData(byte[] data) {
        super(data);
    }

    @Override
    public ByteData getOffsetData(long offset) {
        return new OffsetBackedByteData(d, offset);
    }

    @Override
    public OffsettingByteData getOffsettingOffsetData(long offset) {
        return new OffsettingBackedByteData(d, offset);
    }

    @Override
    int getRealOffset(int offset) {
        return offset;
    }
    
}
