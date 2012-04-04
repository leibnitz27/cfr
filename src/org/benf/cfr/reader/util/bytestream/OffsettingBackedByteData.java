package org.benf.cfr.reader.util.bytestream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 19:49
 * To change this template use File | Settings | File Templates.
 */
public class OffsettingBackedByteData extends AbstractBackedByteData implements OffsettingByteData {
    final byte[]data;
    final int originalOffset;
    int mutableOffset;

    public OffsettingBackedByteData(byte[] data, long offset)
    {
        this.data = data;
        this.originalOffset = (int)offset;
        this.mutableOffset = 0;
    }

    @Override
    public void advance(long offset)
    {
        mutableOffset += offset;
    }

    @Override
    public void rewind(long offset) {
        mutableOffset -= offset;
    }

    @Override
    public long getOffset()
    {
        return mutableOffset;
    }

    @Override
    public DataInputStream rawDataAsStream(int start, int len)
    {
        return new DataInputStream(new ByteArrayInputStream(data, start+originalOffset+mutableOffset, len));
    }

    @Override
    public ByteData getOffsetData(long offset)
    {
        return new OffsetBackedByteData(data, originalOffset+mutableOffset+offset);
    }

    @Override
    public OffsettingByteData getOffsettingOffsetData(long offset)
    {
        return new OffsettingBackedByteData(data, originalOffset+mutableOffset+offset);
    }

    @Override
    public byte getU1At(long o)
    {
        return data[(int)(originalOffset+mutableOffset+o)];
    }

    @Override
    public byte[] getBytesAt(int count, long offset)
    {
        byte [] res = new byte[count];
        System.arraycopy(data, (int)(this.originalOffset + this.mutableOffset + offset), res, 0, count);
        return res;
    }
}
