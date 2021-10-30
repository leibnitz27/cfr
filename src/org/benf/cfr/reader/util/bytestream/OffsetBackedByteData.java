package org.benf.cfr.reader.util.bytestream;

public class OffsetBackedByteData extends AbstractBackedByteData {
    private final int offset;

    OffsetBackedByteData(byte[] data, long offset) {
        super(data);
        this.offset = (int) offset;
    }

    @Override
    public ByteData getOffsetData(long offset) {
        return new OffsetBackedByteData(d, this.offset + offset);
    }

    @Override
    public OffsettingByteData getOffsettingOffsetData(long offset) {
        return new OffsettingBackedByteData(d, this.offset + offset);
    }

    @Override
    int getRealOffset(int o) {
        return o + offset;
    }
}
