package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

public interface ConstantPoolEntry {

    long getRawByteLength();

    void dump(Dumper d);

    enum Type {
        CPT_UTF8,
        CPT_Integer,
        CPT_Float,
        CPT_Long,
        CPT_Double,
        CPT_Class,
        CPT_String,
        CPT_FieldRef,
        CPT_MethodRef,
        CPT_InterfaceMethodRef,
        CPT_NameAndType,
        CPT_MethodHandle,
        CPT_MethodType,
        CPT_InvokeDynamic;

        private static final byte VAL_UTF8 = 1;
        private static final byte VAL_Integer = 3;
        private static final byte VAL_Float = 4;
        private static final byte VAL_Long = 5;
        private static final byte VAL_Double = 6;
        private static final byte VAL_Class = 7;
        private static final byte VAL_String = 8;
        private static final byte VAL_FieldRef = 9;
        private static final byte VAL_MethodRef = 10;
        private static final byte VAL_InterfaceMethodRef = 11;
        private static final byte VAL_NameAndType = 12;
        private static final byte VAL_MethodHandle = 15;
        private static final byte VAL_MethodType = 16;
        private static final byte VAL_InvokeDynamic = 18;

        public static Type get(byte val) {
            switch (val) {
                case VAL_UTF8:
                    return CPT_UTF8;
                case VAL_Integer:
                    return CPT_Integer;
                case VAL_Float:
                    return CPT_Float;
                case VAL_Long:
                    return CPT_Long;
                case VAL_Double:
                    return CPT_Double;
                case VAL_Class:
                    return CPT_Class;
                case VAL_String:
                    return CPT_String;
                case VAL_FieldRef:
                    return CPT_FieldRef;
                case VAL_MethodRef:
                    return CPT_MethodRef;
                case VAL_InterfaceMethodRef:
                    return CPT_InterfaceMethodRef;
                case VAL_NameAndType:
                    return CPT_NameAndType;
                case VAL_MethodHandle:
                    return CPT_MethodHandle;
                case VAL_MethodType:
                    return CPT_MethodType;
                case VAL_InvokeDynamic:
                    return CPT_InvokeDynamic;
                default:
                    throw new ConfusedCFRException("Invalid constant pool entry type : " + val);
            }
        }
    }
}
