package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryUTF8 implements ConstantPoolEntry {
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final long OFFSET_OF_LENGTH = 1;
    private static final long OFFSET_OF_DATA = 3;

    private final short length;
    private final String value;

    public ConstantPoolEntryUTF8(ByteData data) {
        this.length = data.getS2At(OFFSET_OF_LENGTH);
        byte[] bytes = data.getBytesAt(length, OFFSET_OF_DATA);
        this.value = new String(bytes, UTF8_CHARSET);
    }

    @Override
    public long getRawByteLength() {
        return 3 + length;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print("CONSTANT_UTF8 value=" + value);
    }

    @Override
    public String toString() {
        return "ConstantUTF8[" + value + "]";
    }
}
