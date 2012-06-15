package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.stack.StackType;
import org.benf.cfr.reader.bytecode.analysis.stack.StackTypes;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 06:08
 * To change this template use File | Settings | File Templates.
 */
public enum JVMInstr {
    /* opcode, numimmed, numpopped, numpushed, num targets */
    /* numimmed is length (-1 if variable) of arguments immediately following opcode */
    AALOAD(0x32, 0, new StackTypes(StackType.REF, StackType.INT), StackType.REF.asList()),
    AASTORE(0x53, 0, new StackTypes(StackType.REF, StackType.INT, StackType.REF), StackTypes.EMPTY),
    ACONST_NULL(0x01, 0, StackTypes.EMPTY, StackType.REF.asList()),
    ALOAD(0x19, 1, StackTypes.EMPTY, StackType.REF.asList()),
    ALOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.REF.asList()),
    ALOAD_0(0x2a, 0, StackTypes.EMPTY, StackType.REF.asList()),
    ALOAD_1(0x2b, 0, StackTypes.EMPTY, StackType.REF.asList()),
    ALOAD_2(0x2c, 0, StackTypes.EMPTY, StackType.REF.asList()),
    ALOAD_3(0x2d, 0, StackTypes.EMPTY, StackType.REF.asList()),
    ANEWARRAY(0xbd, 2, StackType.INT.asList(), StackType.REF.asList(), new OperationFactoryCPEntryW()),
    ARETURN(0xb0, 0, StackType.REF.asList(), StackTypes.EMPTY, new OperationFactoryReturn()),
    ARRAYLENGTH(0xbe, 0, StackType.REF.asList(), StackType.INT.asList()),
    ASTORE(0x3a, 1, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY),
    ASTORE_WIDE(-1, 3, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY),
    ASTORE_0(0x4b, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY),
    ASTORE_1(0x4c, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY),
    ASTORE_2(0x4d, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY),
    ASTORE_3(0x4e, 0, StackType.RETURNADDRESSORREF.asList(), StackTypes.EMPTY),
    ATHROW(0xbf, 0, StackType.REF.asList(), StackType.REF.asList(), new OperationFactoryThrow()),
    BALOAD(0x33, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList()),
    BASTORE(0x54, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY),
    BIPUSH(0x10, 1, StackTypes.EMPTY, StackType.INT.asList()),
    CALOAD(0x34, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList()),
    CASTORE(0x55, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY),
    CHECKCAST(0xc0, 2, StackType.REF.asList(), StackType.REF.asList()),
    D2F(0x90, 0, StackType.DOUBLE.asList(), StackType.FLOAT.asList()),
    D2I(0x8e, 0, StackType.DOUBLE.asList(), StackType.INT.asList()),
    D2L(0x8f, 0, StackType.DOUBLE.asList(), StackType.LONG.asList()),
    DADD(0x63, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList()),
    DALOAD(0x31, 0, new StackTypes(StackType.REF, StackType.INT), StackType.DOUBLE.asList()),
    DASTORE(0x52, 0, new StackTypes(StackType.REF, StackType.INT, StackType.DOUBLE), StackTypes.EMPTY),
    DCMPG(0x98, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.INT.asList()),
    DCMPL(0x97, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.INT.asList()),
    DCONST_0(0xe, 0, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DCONST_1(0xf, 0, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DDIV(0x6f, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList()),
    DLOAD(0x18, 1, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DLOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DLOAD_0(0x26, 0, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DLOAD_1(0x27, 0, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DLOAD_2(0x28, 0, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DLOAD_3(0x29, 0, StackTypes.EMPTY, StackType.DOUBLE.asList()),
    DMUL(0x6b, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList()),
    DNEG(0x77, 0, StackType.DOUBLE.asList(), StackType.DOUBLE.asList()),
    DREM(0x73, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList()),
    DRETURN(0xaf, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY, new OperationFactoryReturn()),
    DSTORE(0x39, 1, StackType.DOUBLE.asList(), StackTypes.EMPTY),
    DSTORE_WIDE(-1, 3, StackType.DOUBLE.asList(), StackTypes.EMPTY),
    DSTORE_0(0x47, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY),
    DSTORE_1(0x48, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY),
    DSTORE_2(0x49, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY),
    DSTORE_3(0x4a, 0, StackType.DOUBLE.asList(), StackTypes.EMPTY),
    DSUB(0x67, 0, new StackTypes(StackType.DOUBLE, StackType.DOUBLE), StackType.DOUBLE.asList()),
    // DUP operations have behaviour which is dependent on stack content.
    DUP(0x59, 0, null, null, new OperationFactoryDup()), // 1 -> 2 (checkType)
    DUP_X1(0x5a, 0, null, null, new OperationFactoryDupX1()),
    DUP_X2(0x5b, 0, null, null, new OperationFactoryDupX2()),
    DUP2(0x5c, 0, null, null, new OperationFactoryDup2()),
    DUP2_X1(0x5d, 0, null, null, new OperationFactoryDup2X1()),
    DUP2_X2(0x5e, 0, null, null, new OperationFactoryDup2X2()),
    F2D(0x8d, 0, StackType.FLOAT.asList(), StackType.DOUBLE.asList()),
    F2I(0x8b, 0, StackType.FLOAT.asList(), StackType.INT.asList()),
    F2L(0x8c, 0, StackType.FLOAT.asList(), StackType.LONG.asList()),
    FADD(0x62, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList()),
    FALOAD(0x30, 0, new StackTypes(StackType.REF, StackType.INT), StackType.FLOAT.asList()),
    FASTORE(0x51, 0, new StackTypes(StackType.REF, StackType.INT, StackType.FLOAT), StackTypes.EMPTY),
    FCMPG(0x96, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.INT.asList()),
    FCMPL(0x95, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.INT.asList()),
    FCONST_0(0xb, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FCONST_1(0xc, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FCONST_2(0xd, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FDIV(0x6e, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList()),
    FLOAD(0x17, 1, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FLOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FLOAD_0(0x22, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FLOAD_1(0x23, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FLOAD_2(0x24, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FLOAD_3(0x25, 0, StackTypes.EMPTY, StackType.FLOAT.asList()),
    FMUL(0x6a, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList()),
    FNEG(0x76, 0, StackType.FLOAT.asList(), StackType.FLOAT.asList()),
    FREM(0x72, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList()),
    FRETURN(0xae, 0, StackType.FLOAT.asList(), StackTypes.EMPTY, new OperationFactoryReturn()),
    FSTORE(0x38, 1, StackType.FLOAT.asList(), StackTypes.EMPTY),
    FSTORE_WIDE(-1, 3, StackType.FLOAT.asList(), StackTypes.EMPTY),
    FSTORE_0(0x43, 0, StackType.FLOAT.asList(), StackTypes.EMPTY),
    FSTORE_1(0x44, 0, StackType.FLOAT.asList(), StackTypes.EMPTY),
    FSTORE_2(0x45, 0, StackType.FLOAT.asList(), StackTypes.EMPTY),
    FSTORE_3(0x46, 0, StackType.FLOAT.asList(), StackTypes.EMPTY),
    FSUB(0x66, 0, new StackTypes(StackType.FLOAT, StackType.FLOAT), StackType.FLOAT.asList()),
    GETFIELD(0xb4, 2, null, null, new OperationFactoryGetField()),
    GETSTATIC(0xb2, 2, null, null, new OperationFactoryGetStatic()),
    GOTO(0xa7, 2, StackTypes.EMPTY, StackTypes.EMPTY, new OperationFactoryGoto()),
    GOTO_W(0xc8, 4, StackTypes.EMPTY, StackTypes.EMPTY, new OperationFactoryGotoW()),
    I2B(0x91, 0, StackType.INT.asList(), StackType.INT.asList()),
    I2C(0x92, 0, StackType.INT.asList(), StackType.INT.asList()),
    I2D(0x87, 0, StackType.INT.asList(), StackType.DOUBLE.asList()),
    I2F(0x86, 0, StackType.INT.asList(), StackType.FLOAT.asList()),
    I2L(0x85, 0, StackType.INT.asList(), StackType.LONG.asList()),
    I2S(0x93, 0, StackType.INT.asList(), StackType.INT.asList()),
    IADD(0x60, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IALOAD(0x2E, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList()),
    IAND(0x7E, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IASTORE(0x4F, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY),
    ICONST_M1(0x2, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ICONST_0(0x3, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ICONST_1(0x4, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ICONST_2(0x5, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ICONST_3(0x6, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ICONST_4(0x7, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ICONST_5(0x8, 0, StackTypes.EMPTY, StackType.INT.asList()),
    IDIV(0x6c, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IF_ACMPEQ(0xa5, 2, new StackTypes(StackType.REF, StackType.REF), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ACMPNE(0xa6, 2, new StackTypes(StackType.REF, StackType.REF), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ICMPEQ(0x9f, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ICMPNE(0xa0, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ICMPLT(0xa1, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ICMPGE(0xa2, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ICMPGT(0xa3, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IF_ICMPLE(0xa4, 2, new StackTypes(StackType.INT, StackType.INT), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFEQ(0x99, 2, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFNE(0x9a, 2, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFLT(0x9b, 2, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFGE(0x9c, 2, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFGT(0x9d, 2, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFLE(0x9e, 2, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFNONNULL(0xc7, 2, StackType.REF.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IFNULL(0xc6, 2, StackType.REF.asList(), StackTypes.EMPTY, new OperationFactoryConditionalJump()),
    IINC(0x84, 2, StackTypes.EMPTY, StackTypes.EMPTY),
    IINC_WIDE(-1, 5, StackTypes.EMPTY, StackTypes.EMPTY),
    ILOAD(0x15, 1, StackTypes.EMPTY, StackType.INT.asList()),
    ILOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.INT.asList()),
    ILOAD_0(0x1a, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ILOAD_1(0x1b, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ILOAD_2(0x1c, 0, StackTypes.EMPTY, StackType.INT.asList()),
    ILOAD_3(0x1d, 0, StackTypes.EMPTY, StackType.INT.asList()),
    IMUL(0x68, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    INEG(0x74, 0, StackType.INT.asList(), StackType.INT.asList()),
    INSTANCEOF(0xc1, 2, StackType.REF.asList(), StackType.INT.asList(), new OperationFactoryCPEntryW()),
    INVOKEDYNAMIC(0xba, 1, null, null),
    INVOKEINTERFACE(0xb9, 4, null, null, new OperationFactoryInvokeInterface()),
    INVOKESPECIAL(0xb7, 2, null, null, new OperationFactoryInvoke(true)),
    INVOKESTATIC(0xb8, 2, null, null, new OperationFactoryInvoke(false)),
    INVOKEVIRTUAL(0xb6, 2, null, null, new OperationFactoryInvoke(true)),
    IOR(0x80, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IREM(0x70, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IRETURN(0xac, 0, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryReturn()),
    ISHL(0x78, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    ISHR(0x7a, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    ISTORE(0x36, 1, StackType.INT.asList(), StackTypes.EMPTY),
    ISTORE_WIDE(-1, 3, StackType.INT.asList(), StackTypes.EMPTY),
    ISTORE_0(0x3b, 0, StackType.INT.asList(), StackTypes.EMPTY),
    ISTORE_1(0x3c, 0, StackType.INT.asList(), StackTypes.EMPTY),
    ISTORE_2(0x3d, 0, StackType.INT.asList(), StackTypes.EMPTY),
    ISTORE_3(0x3e, 0, StackType.INT.asList(), StackTypes.EMPTY),
    ISUB(0x64, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IUSHR(0x7c, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    IXOR(0x82, 0, new StackTypes(StackType.INT, StackType.INT), StackType.INT.asList()),
    JSR(0xa8, 2, StackTypes.EMPTY, StackType.RETURNADDRESS.asList()),
    JSR_W(0xc9, 4, StackTypes.EMPTY, StackType.RETURNADDRESS.asList()),
    L2D(0x8a, 0, StackType.LONG.asList(), StackType.DOUBLE.asList()),
    L2F(0x89, 0, StackType.LONG.asList(), StackType.FLOAT.asList()),
    L2I(0x88, 0, StackType.LONG.asList(), StackType.INT.asList()),
    LADD(0x61, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LALOAD(0x2f, 0, new StackTypes(StackType.REF, StackType.INT), StackType.LONG.asList()),
    LAND(0x7f, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LASTORE(0x50, 0, new StackTypes(StackType.REF, StackType.INT, StackType.LONG), StackTypes.EMPTY),
    LCMP(0x94, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.INT.asList()),
    LCONST_0(0x9, 0, StackTypes.EMPTY, StackType.LONG.asList()),
    LCONST_1(0xa, 0, StackTypes.EMPTY, StackType.LONG.asList()),
    LDC(0x12, 1, null, null, new OperationFactoryLDC()),
    LDC_W(0x13, 2, null, null, new OperationFactoryLDCW()),
    LDC2_W(0x14, 2, null, null, new OperationFactoryLDC2W()),
    LDIV(0x6d, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LLOAD(0x16, 1, StackTypes.EMPTY, StackType.LONG.asList()),
    LLOAD_WIDE(-1, 3, StackTypes.EMPTY, StackType.LONG.asList()),
    LLOAD_0(0x1e, 0, StackTypes.EMPTY, StackType.LONG.asList()),
    LLOAD_1(0x1f, 0, StackTypes.EMPTY, StackType.LONG.asList()),
    LLOAD_2(0x20, 0, StackTypes.EMPTY, StackType.LONG.asList()),
    LLOAD_3(0x21, 0, StackTypes.EMPTY, StackType.LONG.asList()),
    LMUL(0x69, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LNEG(0x75, 0, StackType.LONG.asList(), StackType.LONG.asList()),
    LOOKUPSWITCH(0xab, -1, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryLookupSwitch()),
    LOR(0x81, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LREM(0x71, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LRETURN(0xad, 0, StackType.LONG.asList(), StackTypes.EMPTY, new OperationFactoryReturn()),
    LSHL(0x79, 0, new StackTypes(StackType.LONG, StackType.INT), StackType.LONG.asList()),
    LSHR(0x7b, 0, new StackTypes(StackType.LONG, StackType.INT), StackType.LONG.asList()),
    LSTORE(0x37, 1, StackType.LONG.asList(), StackTypes.EMPTY),
    LSTORE_WIDE(-1, 3, StackType.LONG.asList(), StackTypes.EMPTY),
    LSTORE_0(0x3f, 0, StackType.LONG.asList(), StackTypes.EMPTY),
    LSTORE_1(0x40, 0, StackType.LONG.asList(), StackTypes.EMPTY),
    LSTORE_2(0x41, 0, StackType.LONG.asList(), StackTypes.EMPTY),
    LSTORE_3(0x42, 0, StackType.LONG.asList(), StackTypes.EMPTY),
    LSUB(0x65, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    LUSHR(0x7d, 0, new StackTypes(StackType.LONG, StackType.INT), StackType.LONG.asList()),
    LXOR(0x83, 0, new StackTypes(StackType.LONG, StackType.LONG), StackType.LONG.asList()),
    MONITORENTER(0xc2, 0, StackType.REF.asList(), StackTypes.EMPTY),
    MONITOREXIT(0xc3, 0, StackType.REF.asList(), StackTypes.EMPTY),
    MULTIANEWARRAY(0xc5, 3, null, null),
    NEW(0xbb, 2, StackTypes.EMPTY, StackType.REF.asList(), new OperationFactoryNew()),
    NEWARRAY(0xbc, 1, StackType.INT.asList(), StackType.REF.asList()),
    NOP(0x0, 0, StackTypes.EMPTY, StackTypes.EMPTY),
    POP(0x57, 0, null, null, new OperationFactoryPop()),
    POP2(0x58, 0, null, null, new OperationFactoryPop2()),
    PUTFIELD(0xb5, 2, null, null, new OperationFactoryPutField()),
    PUTSTATIC(0xb3, 2, null, null, new OperationFactoryPutStatic()),
    // TODO : Ret isn't right.
    RET(0xa9, 1, StackTypes.EMPTY, StackTypes.EMPTY, new OperationFactoryReturn()),
    RET_WIDE(-1, 3, StackTypes.EMPTY, StackTypes.EMPTY, new OperationFactoryReturn()),
    RETURN(0xb1, 0, StackTypes.EMPTY, StackTypes.EMPTY, new OperationFactoryReturn()),
    SALOAD(0x35, 0, new StackTypes(StackType.REF, StackType.INT), StackType.INT.asList()),
    SASTORE(0x56, 0, new StackTypes(StackType.REF, StackType.INT, StackType.INT), StackTypes.EMPTY),
    SIPUSH(0x11, 2, StackTypes.EMPTY, StackType.INT.asList()),
    SWAP(0x5f, 0, null, null, new OperationFactorySwap()),
    TABLESWITCH(0xaa, -1, StackType.INT.asList(), StackTypes.EMPTY, new OperationFactoryTableSwitch()),
    WIDE(0xc4, -1, null, null, new OperationFactoryWide()),
    FAKE_TRY(-1, 0, StackTypes.EMPTY, StackTypes.EMPTY),
    FAKE_CATCH(-1, 0, StackTypes.EMPTY, StackType.REF.asList(), new OperationFactoryFakeCatch());

    private final int opcode;
    private final int bytes;
    private final StackTypes stackPopped;
    private final StackTypes stackPushed;
    private final String name;
    private final OperationFactory handler;

    private static final Map<Integer, JVMInstr> opcodeLookup = new HashMap<Integer, JVMInstr>();

    static {
        for (JVMInstr i : values()) {
            opcodeLookup.put(i.getOpcode(), i);
        }
    }

    JVMInstr(int opcode, int bytes, StackTypes popped, StackTypes pushed) {
        this(opcode, bytes, popped, pushed, OperationFactoryDefault.Handler.INSTANCE.getHandler());
    }

    JVMInstr(int opcode, int bytes, StackTypes popped, StackTypes pushed, OperationFactory handler) {
        this.opcode = opcode;
        this.bytes = bytes;
        this.stackPopped = popped;
        this.stackPushed = pushed;
        this.name = super.toString().toLowerCase();
        this.handler = handler;
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

    public StackDelta getStackDelta(byte[] data, ConstantPool cp, ConstantPoolEntry[] constantPoolEntries, StackSim stackSim) {
        return handler.getStackDelta(this, data, cp, constantPoolEntries, stackSim);
    }

    public Op01WithProcessedDataAndByteJumps createOperation(ByteData bd, ConstantPool cp, int offset) {
        Op01WithProcessedDataAndByteJumps res = handler.createOperation(this, bd, cp, offset);
        return res;
    }

}
