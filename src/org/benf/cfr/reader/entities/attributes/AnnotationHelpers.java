package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
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
        ConstantPoolEntryUTF8 typeName = cp.getUTF8Entry(raw.getU2At(offset));
        offset += 2;
        int numElementPairs = raw.getU2At(offset);
        offset += 2;
        Map<String, ElementValue> elementValueMap = MapFactory.newLinkedMap();
        for (int x = 0; x < numElementPairs; ++x) {
            offset = getElementValuePair(raw, offset, cp, elementValueMap);
        }
        return new Pair<Long, AnnotationTableEntry>(offset, new AnnotationTableEntry(ConstantPoolUtils.decodeTypeTok(typeName.getValue(), cp), elementValueMap));
    }

    private static long getElementValuePair(ByteData raw, long offset, ConstantPool cp, Map<String, ElementValue> res) {
        ConstantPoolEntryUTF8 elementName = cp.getUTF8Entry(raw.getU2At(offset));
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
                ConstantPoolEntry constantPoolEntry = cp.getEntry(raw.getU2At(offset));
                TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntry(cp, constantPoolEntry);
                ElementValue value = new ElementValueConst(typedLiteral);
                value = value.withTypeHint(rawJavaType);
                return new Pair<Long, ElementValue>(offset + 2, value);
            }
            case 's': {
                ConstantPoolEntry constantPoolEntry = cp.getEntry(raw.getU2At(offset));
                TypedLiteral typedLiteral = TypedLiteral.getConstantPoolEntryUTF8((ConstantPoolEntryUTF8) constantPoolEntry);
                return new Pair<Long, ElementValue>(offset + 2, new ElementValueConst(typedLiteral));
            }
            case 'e': {
                ConstantPoolEntryUTF8 enumClassName = cp.getUTF8Entry(raw.getU2At(offset));
                ConstantPoolEntryUTF8 enumEntryName = cp.getUTF8Entry(raw.getU2At(offset + 2));
                return new Pair<Long, ElementValue>(offset + 4, new ElementValueEnum(ConstantPoolUtils.decodeTypeTok(enumClassName.getValue(), cp), enumEntryName.getValue()));
            }
            case 'c': {
                ConstantPoolEntryUTF8 className = cp.getUTF8Entry(raw.getU2At(offset));
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
                int numArrayEntries = raw.getU2At(offset);
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

    public static Pair<Long, AnnotationTableTypeEntry> getTypeAnnotation(ByteData raw, long offset, ConstantPool cp) {
        short targetType = raw.getU1At(offset++);
        TypeAnnotationEntryKind kind;
        switch (targetType) {
            case 0x00:
            case 0x01:
                kind = TypeAnnotationEntryKind.type_parameter_target;
                break;
            case 0x10:
                kind = TypeAnnotationEntryKind.supertype_target;
                break;
            case 0x11:
            case 0x12:
                kind = TypeAnnotationEntryKind.type_parameter_bound_target;
                break;
            case 0x13:
            case 0x14:
            case 0x15:
                kind = TypeAnnotationEntryKind.empty_target;
                break;
            case 0x16:
                kind = TypeAnnotationEntryKind.method_formal_parameter_target;
                break;
            case 0x17:
                kind = TypeAnnotationEntryKind.throws_target;
                break;
            case 0x40:
            case 0x41:
                kind = TypeAnnotationEntryKind.localvar_target;
                break;
            case 0x42:
                kind = TypeAnnotationEntryKind.catch_target;
                break;
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x46:
                kind = TypeAnnotationEntryKind.offset_target;
                break;
            case 0x47:
            case 0x48:
            case 0x49:
            case 0x4a:
            case 0x4b:
                kind = TypeAnnotationEntryKind.localvar_target;
                break;
            default:
                // Shouldn't happen - fallback for compatibility.
                throw new BadAttributeException();
        }
        TypeAnnotationLocation location;
        switch (targetType) {
            case 0x00:
            case 0x10:
            case 0x11:
                location = TypeAnnotationLocation.ClassFile;
                break;
            case 0x01:
            case 0x12:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
                location = TypeAnnotationLocation.method_info;
                break;
            case 0x13:
                location = TypeAnnotationLocation.field_info;
                break;
            default:
                location = TypeAnnotationLocation.Code;
                break;
        }
        Pair<Long, TypeAnnotationTargetInfo> targetInfoPair = readTypeAnnotationTargetInfo(kind, raw, offset);
        offset = targetInfoPair.getFirst();
        TypeAnnotationTargetInfo targetInfo = targetInfoPair.getSecond();

        short type_path_length = raw.getU1At(offset++);
        List<TypePathPart> pathData = ListFactory.newList();
        for (int x=0;x<type_path_length;++x) {
            short type_path_kind = raw.getU1At(offset++);
            short type_argument_index = raw.getU1At(offset++);
            switch (type_path_kind) {
                case 0:
                    pathData.add(TypePathPartArray.INSTANCE);
                    break;
                case 1:
                    pathData.add(TypePathPartNested.INSTANCE);
                    break;
                case 2:
                    pathData.add(TypePathPartBound.INSTANCE);
                    break;
                case 3:
                    pathData.add(new TypePathPartParameterized(type_argument_index));
                    break;
            }
        }
        TypePath path = new TypePath(pathData);

        int type_index = raw.getU2At(offset);
        offset+=2;
        ConstantPoolEntryUTF8 type_entry = cp.getUTF8Entry(type_index);
        JavaTypeInstance type = ConstantPoolUtils.decodeTypeTok(type_entry.getValue(), cp);


        int numElementPairs = raw.getU2At(offset);
        offset += 2;
        Map<String, ElementValue> elementValueMap = MapFactory.newLinkedMap();
        for (int x = 0; x < numElementPairs; ++x) {
            offset = getElementValuePair(raw, offset, cp, elementValueMap);
        }

        AnnotationTableTypeEntry res = new AnnotationTableTypeEntry(kind, location, targetInfo, path, type, elementValueMap);

        return new Pair<Long, AnnotationTableTypeEntry>(offset, res);
    }

    // The spec claims that target_info is a union, however localvar_target is a variable length structure....
    public static Pair<Long, TypeAnnotationTargetInfo> readTypeAnnotationTargetInfo(TypeAnnotationEntryKind kind, ByteData raw, long offset) {
        switch (kind) {
            case type_parameter_target:
                return TypeAnnotationTargetInfo.TypeAnnotationParameterTarget.Read(raw, offset);
            case supertype_target:
                return TypeAnnotationTargetInfo.TypeAnnotationSupertypeTarget.Read(raw, offset);
            case type_parameter_bound_target:
                return TypeAnnotationTargetInfo.TypeAnnotationParameterBoundTarget.Read(raw, offset);
            case empty_target:
                return TypeAnnotationTargetInfo.TypeAnnotationEmptyTarget.Read(raw, offset);
            case method_formal_parameter_target:
                return TypeAnnotationTargetInfo.TypeAnnotationFormalParameterTarget.Read(raw, offset);
            case throws_target:
                return TypeAnnotationTargetInfo.TypeAnnotationThrowsTarget.Read(raw, offset);
            case localvar_target:
                return TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget.Read(raw, offset);
            case catch_target:
                return TypeAnnotationTargetInfo.TypeAnnotationCatchTarget.Read(raw, offset);
            case offset_target:
                return TypeAnnotationTargetInfo.TypeAnnotationOffsetTarget.Read(raw, offset);
            case type_argument_target:
                return TypeAnnotationTargetInfo.TypeAnnotationTypeArgumentTarget.Read(raw, offset);
            default:
                throw new BadAttributeException();
        }
    }

}
