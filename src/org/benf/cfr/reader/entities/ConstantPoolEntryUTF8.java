package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.config.GlobalArgs;
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
public class ConstantPoolEntryUTF8 extends AbstractConstantPoolEntry {
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final long OFFSET_OF_LENGTH = 1;
    private static final long OFFSET_OF_DATA = 3;

    private final short length;
    private final String value;

    private static int idx;

    public ConstantPoolEntryUTF8(ConstantPool cp, ByteData data) {
        super(cp);
        this.length = data.getS2At(OFFSET_OF_LENGTH);
        byte[] bytes = data.getBytesAt(length, OFFSET_OF_DATA);
        String tmpValue = new String(bytes, UTF8_CHARSET);
        if (tmpValue.length() > 512 && GlobalArgs.hideExtremelyLongStrings) {
            tmpValue = "longStr" + idx++ + "[" + tmpValue.substring(0, 10).replace('\r', '_').replace('\n', '_') + "]";
        }
        tmpValue = tmpValue.replace("\n", "\\n").replace("\r", "\\r");
        this.value = tmpValue;
    }

    @Override
    public long getRawByteLength() {
        return 3 + length;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void dump(Dumper d) {
        d.print("CONSTANT_UTF8 value=" + value);
    }

    @Override
    public String toString() {
        return "ConstantUTF8[" + value + "]";
    }
}
