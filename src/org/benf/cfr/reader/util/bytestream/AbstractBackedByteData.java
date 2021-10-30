package org.benf.cfr.reader.util.bytestream;

import org.benf.cfr.reader.util.ConfusedCFRException;

public abstract class AbstractBackedByteData implements ByteData {
    protected final byte[] d;

    protected AbstractBackedByteData(byte[] data) {
        this.d = data;
    }

    abstract int getRealOffset(int offset);

    @Override
    public int getS4At(long o) throws ConfusedCFRException {
        int a = getRealOffset((int) o);
        try {
            return (((d[a] & 0xFF) << 24) | ((d[a + 1] & 0xFF) << 16) | ((d[a + 2] & 0xFF) << 8) | (d[a + 3] & 0xFF));
        } catch (IndexOutOfBoundsException e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public double getDoubleAt(long o) throws ConfusedCFRException {
        return Double.longBitsToDouble(getLongAt(o));
    }

    @Override
    public float getFloatAt(long o) throws ConfusedCFRException {
        return Float.intBitsToFloat(getS4At(o));
    }

    @Override
    public long getLongAt(long o) throws ConfusedCFRException {
        int a = getRealOffset((int) o);
        try {
            return (((long)(d[a + 0] & 0xFF) << 56) +
            ((long)(d[a + 1] & 0xFF) << 48) +
            ((long)(d[a + 2] & 0xFF) << 40) +
            ((long)(d[a + 3] & 0xFF) << 32) +
            ((long)(d[a + 4] & 0xFF) << 24) +
            ((d[a + 5] & 0xFF) << 16) +
            ((d[a + 6] & 0xFF) <<  8) +
            ((d[a + 7] & 0xFF) << 0));
        } catch (IndexOutOfBoundsException e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public short getS2At(long o) throws ConfusedCFRException {
        int a = getRealOffset((int) o);
        try {
            return (short)(((d[a] & 0xFF) << 8) | (d[a + 1] & 0xFF));
        } catch (IndexOutOfBoundsException e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public int getU2At(long o) throws ConfusedCFRException {
        int a = getRealOffset((int) o);
        try {
            return (((d[a] & 0xFF) << 8) | (d[a + 1] & 0xFF));
        } catch (IndexOutOfBoundsException e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public short getU1At(long o) throws ConfusedCFRException {
        int a = getRealOffset((int) o);
        try {
            return (short)(d[a] & 0xff);
        } catch (IndexOutOfBoundsException e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public byte getS1At(long o) {
        int a = getRealOffset((int) o);
        try {
            return d[a];
        } catch (IndexOutOfBoundsException e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public byte[] getBytesAt(int count, long offset) {
        int a = getRealOffset((int) offset);
        byte[] res = new byte[count];
        System.arraycopy(d, a, res, 0, count);
        return res;
    }
}
