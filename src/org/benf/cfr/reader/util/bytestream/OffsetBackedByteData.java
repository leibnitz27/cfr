package org.benf.cfr.reader.util.bytestream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 19:25
 * To change this template use File | Settings | File Templates.
 */
public class OffsetBackedByteData extends AbstractBackedByteData {
    final int offset;
    final byte[] data;

    public OffsetBackedByteData(byte[] data, long offset) {
        this.offset = (int) offset;
        this.data = data;
    }

    @Override
    public DataInputStream rawDataAsStream(int start, int len) {
        return new DataInputStream(new ByteArrayInputStream(data, start + offset, len));
    }

    @Override
    public ByteData getOffsetData(long offset) {
        return new OffsetBackedByteData(data, this.offset + offset);
    }

    @Override
    public OffsettingByteData getOffsettingOffsetData(long offset) {
        return new OffsettingBackedByteData(data, this.offset + offset);
    }

    @Override
    public byte getS1At(long o) {
        return data[(int) (offset + o)];
    }

    @Override
    public byte[] getBytesAt(int count, long offset) {
        byte[] res = new byte[count];
        System.arraycopy(data, (int) (this.offset + offset), res, 0, count);
        return res;
    }
}
