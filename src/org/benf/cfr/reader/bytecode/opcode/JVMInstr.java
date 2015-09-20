package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.HashMap;
import java.util.Map;

public enum JVMInstr {
    /* opcode, numimmed, numpopped, numpushed, num targets */
    /* numimmed is length (-1 if variable) of arguments immediately following opcode */
    AALOAD(0x32, 0, new StackTypes(StackType.REF, StackType.INT), StackType.REF.asList(), RawJavaType.VOID),
    AASTORE(0x53, 0, new StackTypes(StackType.REF, StackType.INT, StackType.REF), StackTypes.EMPTY, RawJavaType.VOID),
    ACONST_NULL(0x01, 0, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.NULL, true),
    ALOAD(0x19, 1, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.VOID, true),
    ALOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.VOID, true),
    ALOAD_0(0x2a, 0, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.REF, true),
    ALOAD_1(0x2b, 0, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.REF, true),
    ALOAD_2(0x2c, 0, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.REF, true),
    ALOAD_3(0x2d, 0, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.REF, true),
    ANEWARRAY(0xbd, 2, StackType.INT.asList(), StackType.REF.asList(), null, new OperationFactoryCPEntryW()),
    ARETURN(0xb0, 0, StackType.REF.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn(), true),
    ARRAYLENGTH(0xbe, 0, StackType.REF.asList(), StackType.INT.asList(), RawJavaType.INT),
    ASTORE(0x3a, 1, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ASTORE_WIDE(-1, 3, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ASTORE_0(0x4b, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ASTORE_1(0x4c, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ASTORE_2(0x4d, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ASTORE_3(0x4e, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ATHROW(0xbf, 0, StackType.REF.asList(), StackType.REF.asList(), RawJavaType.VOID, new OperationFactoryThrow()),
    BALOAD(0x33, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList(), null),
    BASTORE(0x54, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID),
    BIPUSH(0x10, 1, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.VOID),
    CALOAD(0x34, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList(), RawJavaType.CHAR),
    CASTORE(0x55, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID),
    CHECKCAST(0xc0, 2, StackType.REF.asList(), StackType.REF.asList(), RawJavaType.REF, new OperationFactoryCPEntryW()),
    D2F(0x90, 0, StackType.DOUBLE.asList(), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    D2I(0x8e, 0, StackType.DOUBLE.asList(), StackType.INT.asList(), RawJavaType.INT),
    D2L(0x8f, 0, StackType.DOUBLE.asList(), StackType.LONG.asList(), RawJavaType.LONG),
    DADD(0x63, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    DALOAD(0x31, 0, new StackTypes(StackType.REF, StackType.INT), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    DASTORE(0x52, 0, new StackTypes(StackType.REF, StackType.INT, StackType.DOUBLE), StackTypes.EMPTY, RawJavaType.VOID),
    DCMPG(0x98, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.INT.asList(), RawJavaType.INT),
    DCMPL(0x97, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.INT.asList(), RawJavaType.INT),
    DCONST_0(0xe, 0, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DCONST_1(0xf, 0, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DDIV(0x6f, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    DLOAD(0x18, 1, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DLOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DLOAD_0(0x26, 0, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DLOAD_1(0x27, 0, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DLOAD_2(0x28, 0, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DLOAD_3(0x29, 0, StackTypes.EMPTY, StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    DMUL(0x6b, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    DNEG(0x77, 0, StackType.DOUBLE.asList(), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    DREM(0x73, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    DRETURN(0xaf, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn()),
    DSTORE(0x39, 1, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    DSTORE_WIDE(-1, 3, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    DSTORE_0(0x47, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    DSTORE_1(0x48, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    DSTORE_2(0x49, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    DSTORE_3(0x4a, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    DSUB(0x67, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    // DUP operations have behaviour which is dependent on stack content.
    DUP(0x59, 0, null, null, null, new OperationFactoryDup()), // 1 -> 2 (checkType)
    DUP_X1(0x5a, 0, null, null, null, new OperationFactoryDupX1()),
    DUP_X2(0x5b, 0, null, null, null, new OperationFactoryDupX2()),
    DUP2(0x5c, 0, null, null, null, new OperationFactoryDup2()),
    DUP2_X1(0x5d, 0, null, null, null, new OperationFactoryDup2X1()),
    DUP2_X2(0x5e, 0, null, null, null, new OperationFactoryDup2X2()),
    F2D(0x8d, 0, StackType.FLOAT.asList(), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    F2I(0x8b, 0, StackType.FLOAT.asList(), StackType.INT.asList(), RawJavaType.INT),
    F2L(0x8c, 0, StackType.FLOAT.asList(), StackType.LONG.asList(), RawJavaType.LONG),
    FADD(0x62, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    FALOAD(0x30, 0, new StackTypes(StackType.REF, StackType.INT), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    FASTORE(0x51, 0, new StackTypes(StackType.REF, StackType.INT, StackType.FLOAT), StackTypes.EMPTY, RawJavaType.VOID),
    FCMPG(0x96, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.INT.asList(), RawJavaType.INT),
    FCMPL(0x95, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.INT.asList(), RawJavaType.INT),
    FCONST_0(0xb, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FCONST_1(0xc, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FCONST_2(0xd, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FDIV(0x6e, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    FLOAD(0x17, 1, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FLOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FLOAD_0(0x22, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FLOAD_1(0x23, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FLOAD_2(0x24, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FLOAD_3(0x25, 0, StackTypes.EMPTY, StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    FMUL(0x6a, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    FNEG(0x76, 0, StackType.FLOAT.asList(), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    FREM(0x72, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    FRETURN(0xae, 0, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn(), true),
    FSTORE(0x38, 1, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    FSTORE_WIDE(-1, 3, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    FSTORE_0(0x43, 0, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    FSTORE_1(0x44, 0, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    FSTORE_2(0x45, 0, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    FSTORE_3(0x46, 0, StackType.FLOAT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    FSUB(0x66, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    GETFIELD(0xb4, 2, null, null, null, new OperationFactoryGetField()),
    GETSTATIC(0xb2, 2, null, null, null, new OperationFactoryGetStatic()),
    GOTO(0xa7, 2, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryGoto(), true),
    GOTO_W(0xc8, 4, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryGotoW(), true),
    I2B(0x91, 0, StackType.INT.asList(), StackType.INT.asList(), RawJavaType.BYTE),
    I2C(0x92, 0, StackType.INT.asList(), StackType.INT.asList(), RawJavaType.CHAR),
    I2D(0x87, 0, StackType.INT.asList(), StackType.DOUBLE.asList(), RawJavaType.DOUBLE),
    I2F(0x86, 0, StackType.INT.asList(), StackType.FLOAT.asList(), RawJavaType.FLOAT),
    I2L(0x85, 0, StackType.INT.asList(), StackType.LONG.asList(), RawJavaType.LONG, true),
    I2S(0x93, 0, StackType.INT.asList(), StackType.INT.asList(), RawJavaType.SHORT),
    IADD(0x60, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IALOAD(0x2E, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IAND(0x7E, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IASTORE(0x4F, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID),
    ICONST_M1(0x2, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ICONST_0(0x3, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ICONST_1(0x4, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ICONST_2(0x5, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ICONST_3(0x6, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ICONST_4(0x7, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ICONST_5(0x8, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    IDIV(0x6c, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IF_ACMPEQ(0xa5, 2, new StackTypes(StackType.REF, StackType.REF), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump()),
    IF_ACMPNE(0xa6, 2, new StackTypes(StackType.REF, StackType.REF), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump()),
    IF_ICMPEQ(0x9f, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IF_ICMPNE(0xa0, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IF_ICMPLT(0xa1, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IF_ICMPGE(0xa2, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IF_ICMPGT(0xa3, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IF_ICMPLE(0xa4, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFEQ(0x99, 2, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFNE(0x9a, 2, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFLT(0x9b, 2, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFGE(0x9c, 2, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFGT(0x9d, 2, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFLE(0x9e, 2, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFNONNULL(0xc7, 2, StackType.REF.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IFNULL(0xc6, 2, StackType.REF.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryConditionalJump(), true),
    IINC(0x84, 2, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID),
    IINC_WIDE(-1, 5, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID),
    ILOAD(0x15, 1, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ILOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ILOAD_0(0x1a, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ILOAD_1(0x1b, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ILOAD_2(0x1c, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    ILOAD_3(0x1d, 0, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.INT, true),
    IMUL(0x68, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    INEG(0x74, 0, StackType.INT.asList(), StackType.INT.asList(), RawJavaType.INT),
    INSTANCEOF(0xc1, 2, StackType.REF.asList(), StackType.INT.asList(), RawJavaType.BOOLEAN, new OperationFactoryCPEntryW()),
    INVOKEDYNAMIC(0xba, 4, null, null, null, new OperationFactoryInvokeDynamic()),
    INVOKEINTERFACE(0xb9, 4, null, null, null, new OperationFactoryInvokeInterface()),
    INVOKESPECIAL(0xb7, 2, null, null, null, new OperationFactoryInvoke(true)),
    INVOKESTATIC(0xb8, 2, null, null, null, new OperationFactoryInvoke(false)),
    INVOKEVIRTUAL(0xb6, 2, null, null, null, new OperationFactoryInvoke(true)),
    IOR(0x80, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IREM(0x70, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IRETURN(0xac, 0, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn(), true),
    ISHL(0x78, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    ISHR(0x7a, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    ISTORE(0x36, 1, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ISTORE_WIDE(-1, 3, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ISTORE_0(0x3b, 0, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ISTORE_1(0x3c, 0, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ISTORE_2(0x3d, 0, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ISTORE_3(0x3e, 0, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    ISUB(0x64, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IUSHR(0x7c, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    IXOR(0x82, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList(), RawJavaType.INT),
    // Note that a JSR shares the GOTO factory.
    JSR(0xa8, 2, StackTypes.EMPTY, StackType.RETURNADDRESS.asList(), RawJavaType.RETURNADDRESS, new OperationFactoryGoto()),
    JSR_W(0xc9, 4, StackTypes.EMPTY, StackType.RETURNADDRESS.asList(), RawJavaType.RETURNADDRESS, new OperationFactoryGotoW()),
    L2D(0x8a, 0, StackType.LONG.asList(), StackType.DOUBLE.asList(), RawJavaType.DOUBLE, true),
    L2F(0x89, 0, StackType.LONG.asList(), StackType.FLOAT.asList(), RawJavaType.FLOAT, true),
    L2I(0x88, 0, StackType.LONG.asList(), StackType.INT.asList(), RawJavaType.INT, true),
    LADD(0x61, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG, true),
    LALOAD(0x2f, 0, new StackTypes(StackType.REF, StackType.INT), StackType.LONG.asList(), RawJavaType.LONG),
    LAND(0x7f, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    LASTORE(0x50, 0, new StackTypes(StackType.REF, StackType.INT, StackType.LONG), StackTypes.EMPTY, RawJavaType.VOID),
    LCMP(0x94, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.INT.asList(), RawJavaType.INT, true),
    LCONST_0(0x9, 0, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LCONST_1(0xa, 0, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LDC(0x12, 1, null, null, null, new OperationFactoryLDC(), true),
    LDC_W(0x13, 2, null, null, null, new OperationFactoryLDCW(), true),
    LDC2_W(0x14, 2, null, null, null, new OperationFactoryLDC2W(), true),
    LDIV(0x6d, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    LLOAD(0x16, 1, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LLOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LLOAD_0(0x1e, 0, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LLOAD_1(0x1f, 0, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LLOAD_2(0x20, 0, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LLOAD_3(0x21, 0, StackTypes.EMPTY, StackType.LONG.asList(), RawJavaType.LONG, true),
    LMUL(0x69, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    LNEG(0x75, 0, StackType.LONG.asList(), StackType.LONG.asList(), RawJavaType.LONG),
    LOOKUPSWITCH(0xab, -1, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryLookupSwitch()),
    LOR(0x81, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    LREM(0x71, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    LRETURN(0xad, 0, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn(), true),
    LSHL(0x79, 0, new StackTypes(StackType.LONG, StackType.INT), StackType.LONG.asList(), RawJavaType.LONG),
    LSHR(0x7b, 0, new StackTypes(StackType.LONG, StackType.INT), StackType.LONG.asList(), RawJavaType.LONG),
    LSTORE(0x37, 1, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    LSTORE_WIDE(-1, 3, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    LSTORE_0(0x3f, 0, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    LSTORE_1(0x40, 0, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    LSTORE_2(0x41, 0, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    LSTORE_3(0x42, 0, StackType.LONG.asList(), StackTypes.EMPTY, RawJavaType.VOID, true),
    LSUB(0x65, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    LUSHR(0x7d, 0, new StackTypes(StackType.LONG, StackType.INT), StackType.LONG.asList(), RawJavaType.LONG),
    LXOR(0x83, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList(), RawJavaType.LONG),
    MONITORENTER(0xc2, 0, StackType.REF.asList(), StackTypes.EMPTY, RawJavaType.VOID),
    MONITOREXIT(0xc3, 0, StackType.REF.asList(), StackTypes.EMPTY, RawJavaType.VOID),
    MULTIANEWARRAY(0xc5, 3, null, null, RawJavaType.REF, new OperationFactoryMultiANewArray()),
    NEW(0xbb, 2, StackTypes.EMPTY, StackType.REF.asList(), null, new OperationFactoryNew()),
    NEWARRAY(0xbc, 1, StackType.INT.asList(), StackType.REF.asList(), null),
    NOP(0x0, 0, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID),
    POP(0x57, 0, null, null, null, new OperationFactoryPop()),
    POP2(0x58, 0, null, null, null, new OperationFactoryPop2()),
    PUTFIELD(0xb5, 2, null, null, RawJavaType.VOID, new OperationFactoryPutField()),
    PUTSTATIC(0xb3, 2, null, null, RawJavaType.VOID, new OperationFactoryPutStatic()),
    RET(0xa9, 1, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn()),
    RET_WIDE(-1, 3, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn()),
    RETURN(0xb1, 0, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryReturn(), true),
    SALOAD(0x35, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList(), RawJavaType.SHORT),
    SASTORE(0x56, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY, RawJavaType.VOID),
    SIPUSH(0x11, 2, StackTypes.EMPTY, StackType.INT.asList(), RawJavaType.SHORT),
    SWAP(0x5f, 0, null, null, null, new OperationFactorySwap()),
    TABLESWITCH(0xaa, -1, StackType.INT.asList(), StackTypes.EMPTY, RawJavaType.VOID, new OperationFactoryTableSwitch()),
    WIDE(0xc4, -1, null, null, null, new OperationFactoryWide()),
    // Obviously there's no such thing as a try/catch opcode, but it's helpful to have pseudo instructions
    FAKE_TRY(-1, 0, StackTypes.EMPTY, StackTypes.EMPTY, RawJavaType.VOID),
    FAKE_CATCH(-1, 0, StackTypes.EMPTY, StackType.REF.asList(), RawJavaType.REF, new OperationFactoryFakeCatch());

    private final int opcode;
    private final int bytes;
    private final StackTypes stackPopped;
    private final StackTypes stackPushed;
    private final RawJavaType rawJavaType;
    private final String name;
    private final OperationFactory handler;
    private final boolean noThrow;

    private static final Map<Integer, JVMInstr> opcodeLookup = new HashMap<Integer, JVMInstr>();

    static {
        for (JVMInstr i : values()) {
            opcodeLookup.put(i.getOpcode(), i);
        }
    }

    JVMInstr(int opcode, int bytes, StackTypes popped, StackTypes pushed, RawJavaType rawJavaType) {
        this(opcode, bytes, popped, pushed, rawJavaType, OperationFactoryDefault.Handler.INSTANCE.getHandler(), false);
    }

    JVMInstr(int opcode, int bytes, StackTypes popped, StackTypes pushed, RawJavaType rawJavaType, boolean noThrow) {
        this(opcode, bytes, popped, pushed, rawJavaType, OperationFactoryDefault.Handler.INSTANCE.getHandler(), noThrow);
    }

    JVMInstr(int opcode, int bytes, StackTypes popped, StackTypes pushed, RawJavaType rawJavaType, OperationFactory handler) {
        this(opcode, bytes, popped, pushed, rawJavaType, handler, false);
    }

    JVMInstr(int opcode, int bytes, StackTypes popped, StackTypes pushed, RawJavaType rawJavaType, OperationFactory handler, boolean noThrow) {
        this.opcode = opcode;
        this.bytes = bytes;
        this.stackPopped = popped;
        this.stackPushed = pushed;
        this.name = super.toString().toLowerCase();
        this.handler = handler;
        this.rawJavaType = rawJavaType;
        this.noThrow = noThrow;
    }

    public int getOpcode() {
        return opcode;
    }

    public String getName() {
        return name;
    }

    public static JVMInstr find(int opcode) {
        if (opcode < 0) opcode += 256;
        Integer iOpcode = opcode;
        if (!opcodeLookup.containsKey(iOpcode)) {
            throw new ConfusedCFRException("Unknown opcode [" + opcode + "]");
        }
        return opcodeLookup.get(opcode);
    }

    protected int getRawLength() {
        return bytes;
    }

    /*
     * Only call from OperationHandler
     */
    public StackTypes getRawStackPushed() {
        return stackPushed;
    }

    public StackTypes getRawStackPopped() {
        return stackPopped;
    }

    public StackDelta getStackDelta(byte[] data, ConstantPoolEntry[] constantPoolEntries, StackSim stackSim, Method method) {
        return handler.getStackDelta(this, data, constantPoolEntries, stackSim, method);
    }

    public Op01WithProcessedDataAndByteJumps createOperation(ByteData bd, ConstantPool cp, int offset) {
        Op01WithProcessedDataAndByteJumps res = handler.createOperation(this, bd, cp, offset);
        return res;
    }

    public RawJavaType getRawJavaType() {
        return rawJavaType;
    }

    public boolean isNoThrow() {
        return noThrow;
    }

    public static boolean isAStore(JVMInstr instr) {
        switch (instr) {
            case ASTORE:
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
            case ASTORE_WIDE:
                return true;
        }
        return false;
    }
}
