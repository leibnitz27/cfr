package org.benf.cfr.reader.util.bytestream;

public interface ByteData {
    byte getS1At(long offset);

    short getU1At(long offset);

    short getS2At(long offset);

    int getU2At(long offset);

    int getS4At(long offset);

    double getDoubleAt(long o);

    float getFloatAt(long o);

    long getLongAt(long o);

    byte[] getBytesAt(int count, long offset);

    ByteData getOffsetData(long offset);

    OffsettingByteData getOffsettingOffsetData(long offset);
}
