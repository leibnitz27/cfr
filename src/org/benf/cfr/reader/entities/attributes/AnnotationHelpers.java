package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.annotations.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Map;

public class AnnotationHelpers {

    public static Pair<Long, AnnotationTableEntry> getAnnotation(ByteData raw, long offset, ConstantPool cp) {
        ConstantPoolEntryUTF8 typeName = cp.getUTF8Entry(raw.getS2At(offset));
        offset += 2;
        int numElementPairs = raw.getS2At(offset);
        offset += 2;
        Map<String, ElementValue> elementValueMap = MapFactory.newLinkedMap();
        for (int x = 0; x < numElementPairs; ++x) {
            offset = getElementValuePair(raw, offset, cp, elementValueMap);
        }
        return new Pair<Long, AnnotationTableEntry>(offset, new AnnotationTableEntry(ConstantPoolUtils.decodeTypeTok(typeName.getValue(), cp), elementValueMap));
    }

    private static long getElementValuePair(ByteData raw, long offset, ConstantPool cp, Map<String, ElementValue> res) {
        ConstantPoolEntryUTF8 elementName = cp.getUTF8Entry(raw.getS2At(offset));
        offset += 2;
        Pair<Long, ElementValue> elementValueP = getElementValue(raw, offset, cp);
        offset = elementValueP.getFirst();
        res.put(elementName.getValue(), elementValueP.getSecond());
        return offset;
    }

    public static Pair<Long, ElementValue> getElementValue(ByteData raw, long offset, ConstantPool cp) {

        char c = (char) raw.getU1At(offset);
        offset++;
        switch (c) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z': {
                RawJavaType rawJavaType = ConstantPoolUtils.decodeRawJavaType(c);
                ConstantPoolEntry constantPoolEntry = cp.getEntry(raw.getS2At(offset));
                TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntry(cp, constantPoolEntry);
                return new Pair<Long, ElementValue>(offset + 2, new ElementValueConst(typedLiteral));
            }
            case 's': {
                ConstantPoolEntry constantPoolEntry = cp.getEntry(raw.getS2At(offset));
                TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntryUTF8((ConstantPoolEntryUTF8) constantPoolEntry);
                return new Pair<Long, ElementValue>(offset + 2, new ElementValueConst(typedLiteral));
            }
            case 'e': {
                ConstantPoolEntryUTF8 enumClassName = cp.getUTF8Entry(raw.getS2At(offset));
                ConstantPoolEntryUTF8 enumEntryName = cp.getUTF8Entry(raw.getS2At(offset + 2));
                return new Pair<Long, ElementValue>(offset + 4, new ElementValueEnum(ConstantPoolUtils.decodeTypeTok(enumClassName.getValue(), cp), enumEntryName.getValue()));
            }
            case 'c': {
                ConstantPoolEntryUTF8 className = cp.getUTF8Entry(raw.getS2At(offset));
                String typeName = className.getValue();
                if (typeName.equals("V")) {
                    return new Pair<Long, ElementValue>(offset + 2, new ElementValueClass(RawJavaType.VOID));
                } else {
                    return new Pair<Long, ElementValue>(offset + 2, new ElementValueClass(ConstantPoolUtils.decodeTypeTok(typeName, cp)));
                }
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
                throw new ConfusedCFRException("Illegal attribute tag [" + c + "]");
        }
    }

}
