package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 21/05/2012
 */
public class ExceptionGroup {

    private final short bytecodeIndexFrom;        // [ a
    private short byteCodeIndexTo;          // ) b    st a <= x < b
    private List<ExceptionTableEntry> entries = ListFactory.newList();
    private final BlockIdentifier tryBlockIdentifier;

    public ExceptionGroup(short bytecodeIndexFrom, BlockIdentifier blockIdentifier) {
        this.bytecodeIndexFrom = bytecodeIndexFrom;
        this.tryBlockIdentifier = blockIdentifier;
    }

    public void add(ExceptionTableEntry entry) {
        this.entries.add(entry);
        if (entry.getBytecode_index_to() > byteCodeIndexTo) byteCodeIndexTo = entry.getBytecode_index_to();
    }

    public List<ExceptionTableEntry> getEntries() {

        return entries;
    }

    public short getBytecodeIndexFrom() {
        return bytecodeIndexFrom;
    }

    public short getByteCodeIndexTo() {
        return byteCodeIndexTo;
    }

    public BlockIdentifier getTryBlockIdentifier() {
        return tryBlockIdentifier;
    }

    @Override
    public String toString() {
        return "[egrp : " + tryBlockIdentifier + "]";
    }
}
