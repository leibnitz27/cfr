package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
/*
 * See 4.4.4 in Java class file format.
 *
 * ClassSignature:
 *   FormalTypeParametersopt SuperclassSignature SuperinterfaceSignature*
 *
 * FormalTypeParameters:
 *   <FormalTypeParameter+>
 *
 * FormalTypeParameter:
 *   Identifier ClassBound InterfaceBound*
 *
 * ClassBound:
 *  : FieldTypeSignatureopt
 *
 * InterfaceBound:
 *  : FieldTypeSignature
 *
 * SuperclassSignature:
 *   ClassTypeSignature
 *
 * SuperinterfaceSignature:
 *   ClassTypeSignature
 *
 * FieldTypeSignature:
 *   ClassTypeSignature
 *   ArrayTypeSignature
 *   TypeVariableSignature
 */
public class AttributeSignature extends Attribute {
    public static final String ATTRIBUTE_NAME = "Signature";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;

    private final int length;
    private final ConstantPool cp;
    private final ConstantPoolEntryUTF8 signature;

    public AttributeSignature(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.cp = cp;
        this.signature = cp.getUTF8Entry(raw.getS2At(OFFSET_OF_REMAINDER));
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print("Signature : " + signature);
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    public ConstantPoolEntryUTF8 getSignature() {
        return signature;
    }
}
