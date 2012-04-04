package org.benf.cfr.reader.util.bytestream;

import org.benf.cfr.reader.util.ConfusedCFRException;

import java.io.DataInputStream;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:27
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractBackedByteData implements ByteData {

    abstract DataInputStream rawDataAsStream(int offset, int length);

    @Override
    public int getU4At(long o) throws ConfusedCFRException
    {
        // Let's find an EFFICIENT way to do this later!
        DataInputStream dis = rawDataAsStream((int)o, 4);
        try {
            return dis.readInt();
        } catch (Exception e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public double getDoubleAt(long o) throws ConfusedCFRException {
        DataInputStream dis = rawDataAsStream((int)o, 8);
        try {
            return dis.readDouble();
        } catch (Exception e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public long getLongAt(long o) throws ConfusedCFRException {
        DataInputStream dis = rawDataAsStream((int)o, 8);
        try {
            return dis.readLong();
        } catch (Exception e) {
            throw new ConfusedCFRException(e);
        }
    }

    @Override
    public short getU2At(long o) throws ConfusedCFRException
    {
        // Let's find an EFFICIENT way to do this later!
        DataInputStream dis = rawDataAsStream((int)o, 2);
        try
        {
            return dis.readShort();
        }
        catch (Exception e)
        {
            throw new ConfusedCFRException(e);
        }
    }

}
