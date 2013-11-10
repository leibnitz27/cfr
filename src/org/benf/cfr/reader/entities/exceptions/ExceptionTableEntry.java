package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.ConfusedCFRException;
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

    private final int priority;

    public ExceptionTableEntry(ByteData raw, int priority) {
        this(
                raw.getS2At(OFFSET_INDEX_FROM),
                raw.getS2At(OFFSET_INDEX_TO),
                raw.getS2At(OFFSET_INDEX_HANDLER),
                raw.getS2At(OFFSET_CATCH_TYPE),
                priority);
    }

    public ExceptionTableEntry(short from, short to, short handler, short catchType, int priority) {
        this.bytecode_index_from = from;
        this.bytecode_index_to = to;
        this.bytecode_index_handler = handler;
        this.catch_type = catchType;
        this.priority = priority;
        if (to < from) {
            throw new IllegalStateException("Malformed exception block, to < from");
        }
    }

    // TODO : Refactor into constructor.
    public JavaRefTypeInstance getCatchType(ConstantPool cp) {
        if (catch_type == 0) {
            return cp.getClassCache().getRefClassFor(TypeConstants.throwableName);
        } else {
            return (JavaRefTypeInstance) cp.getClassEntry(catch_type).getTypeInstance();
        }
    }

    public ExceptionTableEntry copyWithRange(short from, short to) {
        return new ExceptionTableEntry(from, to, this.bytecode_index_handler, this.catch_type, this.priority);
    }

    public short getBytecodeIndexFrom() {
        return bytecode_index_from;
    }

    public short getBytecodeIndexTo() {
        return bytecode_index_to;
    }

    public short getBytecodeIndexHandler() {
        return bytecode_index_handler;
    }

    public short getCatchType() {
        return catch_type;
    }

    public int getPriority() {
        return priority;
    }

    public ExceptionTableEntry aggregateWith(ExceptionTableEntry later) {
        if ((this.bytecode_index_from >= later.bytecode_index_from) ||
                (this.bytecode_index_to != later.bytecode_index_from)) {
            throw new ConfusedCFRException("Can't aggregate exceptionTableEntries");
        }
        // TODO : Priority is not quite right here.
        return new ExceptionTableEntry(this.bytecode_index_from, later.bytecode_index_to, this.bytecode_index_handler, this.catch_type, this.priority);
    }

    public static UnaryFunction<ByteData, ExceptionTableEntry> getBuilder(ConstantPool cp) {
        return new ExceptionTableEntryBuilder(cp);
    }

    private static class ExceptionTableEntryBuilder implements UnaryFunction<ByteData, ExceptionTableEntry> {
        int idx = 0;

        public ExceptionTableEntryBuilder(ConstantPool cp) {
        }

        @Override
        public ExceptionTableEntry invoke(ByteData arg) {
            return new ExceptionTableEntry(arg, idx++);
        }
    }

    @Override
    public int compareTo(ExceptionTableEntry other) {
        int res = bytecode_index_from - other.bytecode_index_from;
        if (res != 0) return res;
        res = bytecode_index_to - other.bytecode_index_to;
//        res = other.bytecode_index_to - bytecode_index_to;
        if (res != 0) return 0 - res;
        res = bytecode_index_handler - other.bytecode_index_handler;
        return res;
    }

    @Override
    public String toString() {
        return "ExceptionTableEntry " + priority + " : [" + bytecode_index_from + "->" + bytecode_index_to + ") : " + bytecode_index_handler;
    }

}
