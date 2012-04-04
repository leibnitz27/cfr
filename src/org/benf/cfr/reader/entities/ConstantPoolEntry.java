package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 19:17
 * To change this template use File | Settings | File Templates.
 */
public interface ConstantPoolEntry {

    long getRawByteLength();
    void dump(Dumper d, ConstantPool cp);

    public static enum Type
    {
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
        CPT_NameAndType;

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

        public static Type get(byte val)
        {
            switch (val)
            {
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
                default:
                    throw new ConfusedCFRException("Invalid constant pool entry type : " + val);
            }
        }
    }
}
