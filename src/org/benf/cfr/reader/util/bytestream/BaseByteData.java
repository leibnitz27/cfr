package org.benf.cfr.reader.util.bytestream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class BaseByteData extends AbstractBackedByteData {
    private final byte[] data;

    public BaseByteData(byte[] data) {
        this.data = data;
    }

    @Override
    public DataInputStream rawDataAsStream(int start, int len) {
        return new DataInputStream(new ByteArrayInputStream(data, start, len));
    }

    @Override
    public ByteData getOffsetData(long offset) {
        return new OffsetBackedByteData(data, offset);
    }

    @Override
    public OffsettingByteData getOffsettingOffsetData(long offset) {
        return new OffsettingBackedByteData(data, offset);
    }

    @Override
    public byte[] getBytesAt(int count, long offset) {
        byte[] res = new byte[count];
        System.arraycopy(data, (int) offset, res, 0, count);
        return res;
    }

    @Override
    public byte getS1At(long o) {
        return data[(int) o];
    }
}
