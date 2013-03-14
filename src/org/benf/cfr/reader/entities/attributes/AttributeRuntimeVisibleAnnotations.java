package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.annotations.*;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public class AttributeRuntimeVisibleAnnotations extends Attribute {
    public final static String ATTRIBUTE_NAME = "RuntimeVisibleAnnotations";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_NUMBER_OF_ANNOTATIONS = 6;
    private static final long OFFSET_OF_ANNOTATION_TABLE = 8;
    private static final long OFFSET_OF_REMAINDER = 6;
    private final List<AnnotationTableEntry> annotationTableEntryList = ListFactory.newList();

    private final int length;

    public AttributeRuntimeVisibleAnnotations(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        short numAnnotations = raw.getS2At(OFFSET_OF_NUMBER_OF_ANNOTATIONS);
        long offset = OFFSET_OF_ANNOTATION_TABLE;
        for (int x = 0; x < numAnnotations; ++x) {
            Pair<Long, AnnotationTableEntry> ape = getAnnotation(raw, offset, cp);
            offset = ape.getFirst();
            annotationTableEntryList.add(ape.getSecond());
        }
    }


    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    private static Pair<Long, AnnotationTableEntry> getAnnotation(ByteData raw, long offset, ConstantPool cp) {
        ConstantPoolEntryUTF8 typeName = cp.getUTF8Entry(raw.getS2At(offset));
        offset += 2;
        int numElementPairs = raw.getS2At(offset);
        offset += 2;
        Map<String, ElementValue> elementValueMap = MapFactory.newMap();
        for (int x = 0; x < numElementPairs; ++x) {
            offset += getElementValuePair(raw, offset, cp, elementValueMap);
        }
        return new Pair<Long, AnnotationTableEntry>(offset, new AnnotationTableEntry(typeName.getValue(), elementValueMap));
    }

    private static long getElementValuePair(ByteData raw, long offset, ConstantPool cp, Map<String, ElementValue> res) {
        ConstantPoolEntryUTF8 elementName = cp.getUTF8Entry(raw.getS2At(offset));
        offset += 2;
        Pair<Long, ElementValue> elementValueP = getElementValue(raw, offset, cp);
        offset = elementValueP.getFirst();
        res.put(elementName.getValue(), elementValueP.getSecond());
        return offset;
    }


    private static Pair<Long, ElementValue> getElementValue(ByteData raw, long offset, ConstantPool cp) {

        char c = (char) raw.getU1At(offset);
        offset++;
        switch (c) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'T':
            case 'J':
            case 'S':
            case 'Z': {
                RawJavaType rawJavaType = ConstantPoolUtils.decodeRawJavaType(c);
                ConstantPoolEntry constantPoolEntry = cp.getEntry(raw.getS2At(offset));
                TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntry(cp, constantPoolEntry);
                return new Pair<Long, ElementValue>(offset + 4, new ElementValueConst(typedLiteral));
            }
            case 's': {
                ConstantPoolEntry constantPoolEntry = cp.getEntry(raw.getS2At(offset));
                TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntry(cp, constantPoolEntry);
                return new Pair<Long, ElementValue>(offset + 4, new ElementValueConst(typedLiteral));
            }
            case 'e': {
                ConstantPoolEntryUTF8 enumClassName = cp.getUTF8Entry(raw.getS2At(offset));
                ConstantPoolEntryUTF8 enumEntryName = cp.getUTF8Entry(raw.getS2At(offset + 2));
                return new Pair<Long, ElementValue>(offset + 4, new ElementValueEnum(enumClassName.getValue(), enumEntryName.getValue()));
            }
            case 'c': {
                ConstantPoolEntryUTF8 className = cp.getUTF8Entry(raw.getS2At(offset));
                return new Pair<Long, ElementValue>(offset + 4, new ElementValueClass(className.getValue()));
            }
            case '@': {
                Pair<Long, AnnotationTableEntry> ape = getAnnotation(raw, offset, cp);
                return new Pair<Long, ElementValue>(ape.getFirst(), new ElementValueAnnotation(ape.getSecond()));
            }
            case '[': {
                int numArrayEntries = raw.getS2At(offset);
                offset += 2;
                List<ElementValue> res = ListFactory.newList();
                for (int x = 0; x < numArrayEntries; ++x) {
                    Pair<Long, ElementValue> ape = getElementValue(raw, offset, cp);
                    offset = ape.getFirst();
                    res.add(ape.getSecond());
                }
                return new Pair<Long, ElementValue>(offset, new ElementValueArray(res));
            }
            default:
                throw new ConfusedCFRException("Illegal attribute tag " + c);
        }
    }

}
