package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.CodeAnalyser;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionTableEntry;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.List;

public class AttributeCode extends Attribute {
    public static final String ATTRIBUTE_NAME = "Code";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_MAX_STACK = 6;

    private final int length;
    private final short maxStack;
    private final short maxLocals;
    private final int codeLength;
    private final List<ExceptionTableEntry> exceptionTableEntries;
    private final List<Attribute> attributes;
    private final ConstantPool cp;
    private final ByteData rawData;

    private final CodeAnalyser codeAnalyser;

    public AttributeCode(ByteData raw, final ConstantPool cp, ClassFileVersion classFileVersion) {
        this.cp = cp;
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);

        long OFFSET_OF_MAX_LOCALS = 8;
        long OFFSET_OF_CODE_LENGTH = 10;
        long OFFSET_OF_CODE = 14;

        short maxStack = 0;
        short maxLocals = 0;
        int codeLength = 0;
        if (classFileVersion.before(ClassFileVersion.JAVA_1_0)) {
            OFFSET_OF_MAX_LOCALS = 7;
            OFFSET_OF_CODE_LENGTH = 8;
            OFFSET_OF_CODE = 10;

            maxStack = raw.getU1At(OFFSET_OF_MAX_STACK);
            maxLocals = raw.getU1At(OFFSET_OF_MAX_LOCALS);
            codeLength = raw.getU2At(OFFSET_OF_CODE_LENGTH);

        } else {
            maxStack = raw.getS2At(OFFSET_OF_MAX_STACK);
            maxLocals = raw.getS2At(OFFSET_OF_MAX_LOCALS);
            codeLength = raw.getS4At(OFFSET_OF_CODE_LENGTH);
        }
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.codeLength = codeLength;

        final long OFFSET_OF_EXCEPTION_TABLE_LENGTH = OFFSET_OF_CODE + codeLength;
        final long OFFSET_OF_EXCEPTION_TABLE = OFFSET_OF_EXCEPTION_TABLE_LENGTH + 2;

        ArrayList<ExceptionTableEntry> etis = new ArrayList<ExceptionTableEntry>();
        final short numExceptions = raw.getS2At(OFFSET_OF_EXCEPTION_TABLE_LENGTH);
        etis.ensureCapacity(numExceptions);
        final long numBytesExceptionInfo =
                ContiguousEntityFactory.buildSized(raw.getOffsetData(OFFSET_OF_EXCEPTION_TABLE), numExceptions, 8, etis,
                        ExceptionTableEntry.getBuilder(cp));
        this.exceptionTableEntries = etis;

        final long OFFSET_OF_ATTRIBUTES_COUNT = OFFSET_OF_EXCEPTION_TABLE + numBytesExceptionInfo;
        final long OFFSET_OF_ATTRIBUTES = OFFSET_OF_ATTRIBUTES_COUNT + 2;
        final short numAttributes = raw.getS2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                AttributeFactory.getBuilder(cp, classFileVersion));
        this.attributes = tmpAttributes;
        this.rawData = raw.getOffsetData(OFFSET_OF_CODE);
        this.codeAnalyser = new CodeAnalyser(this);
    }

    public void setMethod(Method method) {
        codeAnalyser.setMethod(method);
    }

    public Op04StructuredStatement analyse() {
        return codeAnalyser.getAnalysis(getConstantPool().getDCCommonState());
    }

    public ConstantPool getConstantPool() {
        return cp;
    }

    public AttributeLocalVariableTable getLocalVariableTable() {
        for (Attribute attribute : attributes) {
            if (attribute instanceof AttributeLocalVariableTable) return (AttributeLocalVariableTable) attribute;
        }
        return null;
    }

    public ByteData getRawData() {
        return rawData;
    }

    public List<ExceptionTableEntry> getExceptionTableEntries() {
        return exceptionTableEntries;
    }

    public short getMaxStack() {
        return maxStack;
    }

    public short getMaxLocals() {
        return maxLocals;
    }

    public int getCodeLength() {
        return codeLength;
    }

    @Override
    public Dumper dump(Dumper d) {
        return codeAnalyser.getAnalysis(getConstantPool().getDCCommonState()).dump(d);
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_MAX_STACK + length;
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }


}
