package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.bootstrap.BootstrapMethodInfo;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public class AttributeBootstrapMethods extends Attribute {
    public static final String ATTRIBUTE_NAME = "BootstrapMethods";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;

    private static final long OFFSET_OF_NUM_METHODS = 6;

    private final int length;
    private final List<BootstrapMethodInfo> methodInfoList;

    public AttributeBootstrapMethods(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.methodInfoList = decodeMethods(raw, cp);
    }

    private static List<BootstrapMethodInfo> decodeMethods(ByteData raw, ConstantPool cp) {

        List<BootstrapMethodInfo> res = ListFactory.newList();
        int numMethods = raw.getS2At(OFFSET_OF_NUM_METHODS);
        long offset = OFFSET_OF_NUM_METHODS + 2;
        for (int x = 0; x < numMethods; ++x) {
            short methodRef = raw.getS2At(offset);
            offset += 2;
            short numBootstrapArguments = raw.getS2At(offset);
            offset += 2;
            short[] bootstrapArguments = new short[numBootstrapArguments];
            for (int y = 0; y < numBootstrapArguments; ++y) {
                bootstrapArguments[y] = raw.getS2At(offset);
                offset += 2;
            }
            res.add(new BootstrapMethodInfo(methodRef, bootstrapArguments));
        }
        return res;
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print(ATTRIBUTE_NAME);
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    @Override
    public String toString() {
        return ATTRIBUTE_NAME;
    }
}
