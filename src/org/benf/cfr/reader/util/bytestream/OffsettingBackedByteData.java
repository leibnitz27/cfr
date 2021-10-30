package org.benf.cfr.reader.util.bytestream;

public class OffsettingBackedByteData extends AbstractBackedByteData implements OffsettingByteData {
    private final int originalOffset;
    private int mutableOffset;

    OffsettingBackedByteData(byte[] data, long offset) {
        super(data);
        this.originalOffset = (int) offset;
        this.mutableOffset = 0;
    }

    @Override
    public void advance(long offset) {
        mutableOffset += offset;
    }

    @Override
    public long getOffset() {
        return mutableOffset;
    }

    @Override
    public ByteData getOffsetData(long offset) {
        return new OffsetBackedByteData(d, originalOffset + mutableOffset + offset);
    }

    @Override
    public OffsettingByteData getOffsettingOffsetData(long offset) {
        return new OffsettingBackedByteData(d, originalOffset + mutableOffset + offset);
    }

    @Override
    int getRealOffset(int offset) {
        return originalOffset + mutableOffset + offset;
    }
}
