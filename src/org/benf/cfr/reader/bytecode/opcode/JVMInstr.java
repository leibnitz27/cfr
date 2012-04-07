package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
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
    AALOAD(0x32, 0, 2, 1),
    AASTORE(0x53, 0, 3, 0),
    ACONST_NULL(0x01, 0, 0, 1),
    ALOAD(0x19, 1, 0, 1),
    ALOAD_0(0x2a, 0, 0, 1),
    ALOAD_1(0x2b, 0, 0, 1),
    ALOAD_2(0x2c, 0, 0, 1),
    ALOAD_3(0x2d, 0, 0, 1),
    ANEWARRAY(0xbd, 2, 1, 1, new OperationFactoryCPEntryW()),
    ARETURN(0xb0, 0, 1, 0, new OperationFactoryReturn()),
    ARRAYLENGTH(0xbe, 0, 1, 1),
    ASTORE(0x3a, 1, 1, 0),
    ASTORE_0(0x4b, 0, 1, 0),
    ASTORE_1(0x4c, 0, 1, 0),
    ASTORE_2(0x4d, 0, 1, 0),
    ASTORE_3(0x4e, 0, 1, 0),
    ATHROW(0xbf, 0, 1, 1, new OperationFactoryThrow()),
    BALOAD(0x33, 0, 2, 1),
    BASTORE(0x54, 0, 3, 0),
    BIPUSH(0x10, 1, 0, 1),
    CALOAD(0x34, 0, 2, 1),
    CASTORE(0x55, 0, 3, 0),
    CHECKCAST(0xc0, 2, 1, 1),
    D2F(0x90, 0, 1, 1),
    D2I(0x8e, 0, 1, 1),
    D2L(0x8f, 0, 1, 1),
    DADD(0x63, 0, 2, 1),
    DALOAD(0x31, 0, 2, 1),
    DASTORE(0x52, 0, 3, 0),
    DCMPG(0x98, 0, 2, 1),
    DCMPL(0x97, 0, 2, 1),
    DCONST_0(0xe, 0, 0, 1),
    DCONST_1(0xf, 0, 0, 1),
    DDIV(0x6f, 0, 2, 1),
    DLOAD(0x18, 1, 0, 1),
    DLOAD_0(0x26, 0, 0, 1),
    DLOAD_1(0x27, 0, 0, 1),
    DLOAD_2(0x28, 0, 0, 1),
    DLOAD_3(0x29, 0, 0, 1),
    DMUL(0x6b, 0, 2, 1),
    DNEG(0x77, 0, 1, 1),
    DREM(0x73, 0, 2, 1),
    DRETURN(0xaf, 0, 1, 0, new OperationFactoryReturn()),
    DSTORE(0x39, 1, 1, 0),
    DSTORE_0(0x47, 0, 1, 0),
    DSTORE_1(0x48, 0, 1, 0),
    DSTORE_2(0x49, 0, 1, 0),
    DSTORE_3(0x4a, 0, 1, 0),
    DSUB(0x67, 0, 2, 1),
    DUP(0x59, 0, 1, 2),
    DUP_X1(0x5a, 0, 2, 3),
    DUP_X2(0x5b, 0, 3, 4),
    DUP2(0x5c, 0, 2, 4),
    DUP2_X1(0x5d, 0, 3, 5),
    DUP2_X2(0x5e, 0, 4, 6),
    F2D(0x8d, 0, 1, 1),
    F2I(0x8b, 0, 1, 1),
    F2L(0x8c, 0, 1, 1),
    FADD(0x62, 0, 2, 1),
    FALOAD(0x30, 0, 2, 1),
    FASTORE(0x51, 0, 3, 0),
    FCMPG(0x96, 0, 2, 1),
    FCMPL(0x95, 0, 2, 1),
    FCONST_0(0xb, 0, 0, 1),
    FCONST_1(0xc, 0, 0, 1),
    FCONST_2(0xd, 0, 0, 1),
    FDIV(0x6e, 0, 2, 1),
    FLOAD(0x17, 1, 0, 1),
    FLOAD_0(0x22, 0, 0, 1),
    FLOAD_1(0x23, 0, 0, 1),
    FLOAD_2(0x24, 0, 0, 1),
    FLOAD_3(0x25, 0, 0, 1),
    FMUL(0x6a, 0, 2, 1),
    FNEG(0x76, 0, 1, 1),
    FREM(0x72, 0, 2, 1),
    FRETURN(0xae, 0, 1, 0, new OperationFactoryReturn()),
    FSTORE(0x38, 1, 1, 0),
    FSTORE_0(0x43, 0, 1, 0),
    FSTORE_1(0x44, 0, 1, 0),
    FSTORE_2(0x45, 0, 1, 0),
    FSTORE_3(0x46, 0, 1, 0),
    FSUB(0x66, 0, 2, 1),
    GETFIELD(0xb4, 2, 1, 1, new OperationFactoryCPEntryW()),
    GETSTATIC(0xb2, 2, 0, 1, new OperationFactoryCPEntryW()),
    GOTO(0xa7, 2, 0, 0, new OperationFactoryGoto()),
    GOTO_W(0xc8, 4, 0, 0, new OperationFactoryGotoW()),
    I2B(0x91, 0, 1, 1),
    I2C(0x92, 0, 1, 1),
    I2D(0x87, 0, 1, 1),
    I2F(0x86, 0, 1, 1),
    I2L(0x85, 0, 1, 1),
    I2S(0x93, 0, 1, 1),
    IADD(0x60, 0, 2, 1),
    IALOAD(0x2E, 0, 2, 1),
    IAND(0x7E, 0, 2, 1),
    IASTORE(0x4F, 0, 3, 0),
    ICONST_M1(0x2, 0, 0, 1),
    ICONST_0(0x3, 0, 0, 1),
    ICONST_1(0x4, 0, 0, 1),
    ICONST_2(0x5, 0, 0, 1),
    ICONST_3(0x6, 0, 0, 1),
    ICONST_4(0x7, 0, 0, 1),
    ICONST_5(0x8, 0, 0, 1),
    IDIV(0x6c, 0, 2, 1),
    IF_ACMPEQ(0xa5, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ACMPNE(0xa6, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ICMPEQ(0x9f, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ICMPNE(0xa0, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ICMPGT(0xa1, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ICMPGE(0xa2, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ICMPLT(0xa3, 2, 2, 0, new OperationFactoryConditionalJump()),
    IF_ICMPLE(0xa4, 2, 2, 0, new OperationFactoryConditionalJump()),
    IFEQ(0x99, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFNE(0x9a, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFLT(0x9b, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFGE(0x9c, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFGT(0x9d, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFLE(0x9e, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFNONNULL(0xc7, 2, 1, 0, new OperationFactoryConditionalJump()),
    IFNULL(0xc6, 2, 1, 0, new OperationFactoryConditionalJump()),
    IINC(0x84, 2, 0, 0),
    ILOAD(0x15, 1, 0, 1),
    ILOAD_0(0x1a, 0, 0, 1),
    ILOAD_1(0x1b, 0, 0, 1),
    ILOAD_2(0x1c, 0, 0, 1),
    ILOAD_3(0x1d, 0, 0, 1),
    IMUL(0x68, 0, 2, 1),
    INEG(0x74, 0, 1, 1),
    INSTANCEOF(0xc1, 2, 1, 1, new OperationFactoryCPEntryW()),
    INVOKEDYNAMIC(0xba, 1, -1, 0),
    INVOKEINTERFACE(0xb9, 4, -1, 0, new OperationFactoryInvokeInterface()),
    INVOKESPECIAL(0xb7, 2, -1, 0, new OperationFactoryInvoke(true)),
    INVOKESTATIC(0xb8, 2, -1, 0, new OperationFactoryInvoke(false)),
    INVOKEVIRTUAL(0xb6, 2, -1, 0, new OperationFactoryInvoke(true)),
    IOR(0x80, 0, 2, 1),
    IREM(0x70, 0, 2, 1),
    IRETURN(0xac, 0, 1, 0, new OperationFactoryReturn()),
    ISHL(0x78, 0, 2, 1),
    ISHR(0x7a, 0, 2, 1),
    ISTORE(0x36, 1, 1, 0),
    ISTORE_0(0x3b, 0, 1, 0),
    ISTORE_1(0x3c, 0, 1, 0),
    ISTORE_2(0x3d, 0, 1, 0),
    ISTORE_3(0x3e, 0, 1, 0),
    ISUB(0x64, 0, 2, 1),
    IUSHR(0x7c, 0, 2, 1),
    IXOR(0x82, 0, 2, 1),
    JSR(0xa8, 2, 0, 1),
    JSR_W(0xc9, 4, 0, 1),
    L2D(0x8a, 0, 1, 1),
    L2F(0x89, 0, 1, 1),
    L2I(0x88, 0, 1, 1),
    LADD(0x61, 0, 2, 1),
    LALOAD(0x2f, 0, 2, 1),
    LAND(0x7f, 0, 2, 1),
    LASTORE(0x50, 0, 3, 0),
    LCMP(0x94, 0, 2, 1),
    LCONST_0(0x9, 0, 0, 1),
    LCONST_1(0xa, 0, 0, 1),
    LDC(0x12, 1, 0, 1, new OperationFactoryCPEntry()),
    LDC_W(0x13, 2, 0, 1, new OperationFactoryCPEntryW()),
    LDC2_W(0x14, 2, 0, 1, new OperationFactoryCPEntryW()),
    LDIV(0x6d, 0, 2, 1),
    LLOAD(0x16, 1, 0, 1),
    LLOAD_0(0x1e, 0, 0, 1),
    LLOAD_1(0x1f, 0, 0, 1),
    LLOAD_2(0x20, 0, 0, 1),
    LLOAD_3(0x21, 0, 0, 1),
    LMUL(0x69, 0, 2, 1),
    LNEG(0x75, 0, 1, 1),
    LOOKUPSWITCH(0xab, -1, 1, 0, new OperationFactoryLookupSwitch()),
    LOR(0x81, 0, 2, 1),
    LREM(0x71, 0, 2, 1),
    LRETURN(0xad, 0, 1, 0, new OperationFactoryReturn()),
    LSHL(0x79, 0, 2, 1),
    LSHR(0x7b, 0, 2, 1),
    LSTORE(0x37, 1, 1, 0),
    LSTORE_0(0x3f, 0, 1, 0),
    LSTORE_1(0x40, 0, 1, 0),
    LSTORE_2(0x41, 0, 1, 0),
    LSTORE_3(0x42, 0, 1, 0),
    LSUB(0x65, 0, 2, 1),
    LUSHR(0x7d, 0, 2, 1),
    LXOR(0x83, 0, 2, 1),
    MONITORENTER(0xc2, 0, 1, 0),
    MONITOREXIT(0xc3, 0, 1, 0),
    MULTIANEWARRAY(0xc5, 3, -1, 1),
    NEW(0xbb, 2, 0, 1, new OperationFactoryNew()),
    NEWARRAY(0xbc, 1, 1, 1),
    NOP(0x0, 0, 0, 0),
    POP(0x57, 0, 1, 0),
    POP2(0x58, 0, 2, 0),
    PUTFIELD(0xb5, 2, 2, 0, new OperationFactoryCPEntryW()),
    PUTSTATIC(0xb3, 2, 1, 0, new OperationFactoryCPEntryW()),
    RET(0xa9, 1, 0, 0, new OperationFactoryReturn()),
    RETURN(0xb1, 0, 0, 0, new OperationFactoryReturn()),
    SALOAD(0x35, 0, 2, 1),
    SASTORE(0x56, 0, 3, 0),
    SIPUSH(0x11, 2, 0, 1),
    SWAP(0x5f, 0, 2, 2),
    TABLESWITCH(0xaa, -1, 1, 0, new OperationFactoryTableSwitch()),
    WIDE(0xc4, -1, -1, -1, new OperationFactoryWide()),
    FAKE_TRY(-1, 0, 0, 0),
    FAKE_CATCH(-1, 0, 0, 1);

    private final int opcode;
    private final int bytes;
    private final int stackPopped;
    private final int stackPushed;
    private final String name;
    private final OperationFactory handler;

    private static final Map<Integer, JVMInstr> opcodeLookup = new HashMap<Integer, JVMInstr>();

    static {
        for (JVMInstr i : values()) {
            opcodeLookup.put(i.getOpcode(), i);
        }
    }

    JVMInstr(int opcode, int bytes, int stackPopped, int stackPushed) {
        this(opcode, bytes, stackPopped, stackPushed, OperationFactoryDefault.Handler.INSTANCE.getHandler());
    }

    JVMInstr(int opcode, int bytes, int stackPopped, int stackPushed, OperationFactory handler) {
        this.opcode = opcode;
        this.bytes = bytes;
        this.stackPopped = stackPopped;
        this.stackPushed = stackPushed;
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

    public int getRawStackPushed() {
        return stackPushed;
    }

    public int getRawStackPopped() {
        return stackPopped;
    }

    public StackDelta getStackDelta(byte[] data, ConstantPool cp, ConstantPoolEntry[] constantPoolEntries) {
        return handler.getStackDelta(this, data, cp, constantPoolEntries);
    }

    public Op01WithProcessedDataAndByteJumps createOperation(ByteData bd, ConstantPool cp, int offset) {
        Op01WithProcessedDataAndByteJumps res = handler.createOperation(this, bd, cp, offset);
        return res;
    }

}
