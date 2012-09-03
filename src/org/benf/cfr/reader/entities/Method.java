package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamerFactory;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.attributes.AttributeSignature;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.CollectionUtils;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 17/04/2011
 * Time: 21:32
 * To change this template use File | Settings | File Templates.
 */

/* Too much in common with field - refactor.
 *
 */

public class Method implements KnowsRawSize {
    private static final long OFFSET_OF_ACCESS_FLAGS = 0;
    private static final long OFFSET_OF_NAME_INDEX = 2;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 4;
    private static final long OFFSET_OF_ATTRIBUTES_COUNT = 6;
    private static final long OFFSET_OF_ATTRIBUTES = 8;

    private final long length;
    private final Set<AccessFlagMethod> accessFlags;
    private final Map<String, Attribute> attributes;
    private final short nameIndex;
    private final short descriptorIndex;
    private final AttributeCode codeAttribute;
    private final ConstantPool cp;
    private final VariableNamer variableNamer;

    public Method(ByteData raw, final ConstantPool cp) {
        this.cp = cp;
        this.nameIndex = raw.getS2At(OFFSET_OF_NAME_INDEX);
        this.accessFlags = AccessFlagMethod.build(raw.getS2At(OFFSET_OF_ACCESS_FLAGS));
        this.descriptorIndex = raw.getS2At(OFFSET_OF_DESCRIPTOR_INDEX);
        short numAttributes = raw.getS2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        long attributesLength = ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                new UnaryFunction<ByteData, Attribute>() {
                    @Override
                    public Attribute invoke(ByteData arg) {
                        return AttributeFactory.build(arg, cp);
                    }
                });
        this.attributes = ContiguousEntityFactory.addToMap(new HashMap<String, Attribute>(), tmpAttributes);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;
        Attribute codeAttribute = attributes.get(AttributeCode.ATTRIBUTE_NAME);
        if (codeAttribute == null) {
            this.variableNamer = null;
            this.codeAttribute = null;
        } else {
            this.codeAttribute = (AttributeCode) codeAttribute;
            // This rigamarole is neccessary because we don't provide the factory for the code attribute enough information
            // get get the Method (this).
            this.variableNamer = VariableNamerFactory.getNamer(this.codeAttribute.getLocalVariableTable(), cp);
            this.codeAttribute.setMethod(this);
        }
    }

    private AttributeSignature getSignatureAttribute() {
        Attribute attribute = attributes.get(AttributeSignature.ATTRIBUTE_NAME);
        if (attribute == null) return null;
        return (AttributeSignature) attribute;
    }

    public VariableNamer getVariableNamer() {
        return variableNamer;
    }

    @Override
    public long getRawByteLength() {
        return length;
    }

    public String getName() {
        return cp.getUTF8Entry(nameIndex).getValue();
    }

    /* This is a bit ugly - otherwise though we need to tie a variable namer to this earlier.
     * We can't always use the signature... in an enum, for example, it lies!
     *
     * Method  : <init> name : 30, descriptor 31
     * Descriptor ConstantUTF8[(Ljava/lang/String;I)V]
     * Signature Signature:ConstantUTF8[()V]
     *
     * Since the signature is only ever the descriptor with more data, we can just do a length check
     * to validate this.
     *
     */
    public MethodPrototype getMethodPrototype() {
        AttributeSignature sig = getSignatureAttribute();
        ConstantPoolEntryUTF8 signature = sig == null ? null : sig.getSignature();
        ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(descriptorIndex);
        ConstantPoolEntryUTF8 prototype = null;
        if (signature == null || signature.getValue().length() < descriptor.getValue().length()) {
            prototype = descriptor;
        } else {
            prototype = signature;
        }
        boolean isInstance = !accessFlags.contains(AccessFlagMethod.ACC_STATIC);
        return ConstantPoolUtils.parseJavaMethodPrototype(isInstance, prototype, cp, variableNamer);
    }

    private String getSignatureText() {
        String prefix = CollectionUtils.join(accessFlags, " ");
        return (prefix.isEmpty() ? "" : (prefix + " ")) + getMethodPrototype().getPrototype(cp.getUTF8Entry(nameIndex).getValue());
    }

    public void analyse() {
        if (codeAttribute != null) codeAttribute.analyse();
    }

    public void dump(Dumper d, ConstantPool cp) {
        if (codeAttribute != null) {
            d.print(getSignatureText());
            codeAttribute.dump(d, cp);
        }
    }
}
