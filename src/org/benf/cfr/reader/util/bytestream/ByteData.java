package org.benf.cfr.reader.util.bytestream;

import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:28
 * To change this template use File | Settings | File Templates.
 */
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
