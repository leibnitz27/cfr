package org.benf.cfr.reader.util.bytestream;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 19:57
 * To change this template use File | Settings | File Templates.
 */
public interface OffsettingByteData extends ByteData {
    void advance(long offset);
    void rewind(long offset);
    long getOffset();
}
