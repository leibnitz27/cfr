package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 30/03/2012
 * Time: 06:32
 * To change this template use File | Settings | File Templates.
 */
public class ExceptionTableEntry implements Comparable<ExceptionTableEntry> {
    private static final int OFFSET_INDEX_FROM = 0;
    private static final int OFFSET_INDEX_TO = 2;
    private static final int OFFSET_INDEX_HANDLER = 4;
    private static final int OFFSET_CATCH_TYPE = 6;

    private final short bytecode_index_from;        // [ a
    private final short bytecode_index_to;          // ) b    st a <= x < b
    private final short bytecode_index_handler;
    private final short catch_type;

    public ExceptionTableEntry(ByteData raw) {
        this.bytecode_index_from = raw.getS2At(OFFSET_INDEX_FROM);
        this.bytecode_index_to = raw.getS2At(OFFSET_INDEX_TO);
        this.bytecode_index_handler = raw.getS2At(OFFSET_INDEX_HANDLER);
        this.catch_type = raw.getS2At(OFFSET_CATCH_TYPE);
    }

    public short getBytecode_index_from() {
        return bytecode_index_from;
    }

    public short getBytecode_index_to() {
        return bytecode_index_to;
    }

    public short getBytecode_index_handler() {
        return bytecode_index_handler;
    }

    public short getCatch_type() {
        return catch_type;
    }

    public static UnaryFunction<ByteData, ExceptionTableEntry> getBuilder(ConstantPool cp) {
        return new ExceptionTableEntryBuilder(cp);
    }

    private static class ExceptionTableEntryBuilder implements UnaryFunction<ByteData, ExceptionTableEntry> {
        public ExceptionTableEntryBuilder(ConstantPool cp) {
        }

        @Override
        public ExceptionTableEntry invoke(ByteData arg) {
            return new ExceptionTableEntry(arg);
        }
    }

    @Override
    public int compareTo(ExceptionTableEntry other) {
        int res = bytecode_index_from - other.bytecode_index_from;
        if (res != 0) return res;
        res = bytecode_index_to - other.bytecode_index_to;
        if (res != 0) return res;
        res = bytecode_index_handler - other.bytecode_index_handler;
        return res;
    }

    @Override
    public String toString() {
        return "ExceptionTableEntry : [" + bytecode_index_from + "->" + bytecode_index_to + ") : " + bytecode_index_handler;
    }
}
