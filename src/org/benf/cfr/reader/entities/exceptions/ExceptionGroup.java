package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ComparableUnderEC;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.CommaHelp;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 21/05/2012
 */
public class ExceptionGroup {

    private short bytecodeIndexFrom;        // [ a
    private short byteCodeIndexTo;          // ) b    st a <= x < b
    private short minHandlerStart = Short.MAX_VALUE;
    private List<Entry> entries = ListFactory.newList();
    private final BlockIdentifier tryBlockIdentifier;
    private final ConstantPool cp;

    public ExceptionGroup(short bytecodeIndexFrom, BlockIdentifier blockIdentifier, ConstantPool cp) {
        this.bytecodeIndexFrom = bytecodeIndexFrom;
        this.tryBlockIdentifier = blockIdentifier;
        this.cp = cp;
    }

    public void add(ExceptionTableEntry entry) {
        if (entry.getBytecodeIndexHandler() == entry.getBytecodeIndexFrom()) return;
        if (entry.getBytecodeIndexHandler() < minHandlerStart) minHandlerStart = entry.getBytecodeIndexHandler();
        this.entries.add(new Entry(entry));
        if (entry.getBytecodeIndexTo() > byteCodeIndexTo) byteCodeIndexTo = entry.getBytecodeIndexTo();
//        if (byteCodeIndexTo > minHandlerStart) byteCodeIndexTo = minHandlerStart;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void mutateBytecodeIndexFrom(short bytecodeIndexFrom) {
        this.bytecodeIndexFrom = bytecodeIndexFrom;
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
        StringBuilder sb = new StringBuilder();
        sb.append("[egrp ").append(tryBlockIdentifier).append(" [");
        boolean bfirst = true;
        for (Entry e : entries) {
            bfirst = CommaHelp.comma(bfirst, sb);
            sb.append(e.getPriority());
        }
        sb.append(" : ").append(bytecodeIndexFrom).append("->").append(byteCodeIndexTo).append(")]");
        return sb.toString();
    }

    public class Entry implements ComparableUnderEC {
        private final ExceptionTableEntry entry;
        private final JavaRefTypeInstance refType;

        public Entry(ExceptionTableEntry entry) {
            this.entry = entry;
            this.refType = entry.getCatchType(cp);
        }

        public short getBytecodeIndexTo() {
            return entry.getBytecodeIndexTo();
        }

        public short getBytecodeIndexHandler() {
            return entry.getBytecodeIndexHandler();
        }

        public boolean isJustThrowable() {
            JavaRefTypeInstance type = entry.getCatchType(cp);
            return type.getRawName().equals(TypeConstants.throwableName);
        }

        public int getPriority() {
            return entry.getPriority();
        }

        public JavaRefTypeInstance getCatchType() {
            return refType;
        }

        public ExceptionGroup getExceptionGroup() {
            return ExceptionGroup.this;
        }

        public BlockIdentifier getTryBlockIdentifier() {
            return ExceptionGroup.this.getTryBlockIdentifier();
        }

        @Override
        public String toString() {
            JavaRefTypeInstance name = getCatchType();
            return ExceptionGroup.this.toString() + " " + name.getRawName();
        }

        @Override
        public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
            if (o == null) return false;
            if (o == this) return true;
            if (getClass() != o.getClass()) return false;
            Entry other = (Entry) o;
            if (!constraint.equivalent(entry, other.entry)) return false;
            if (!constraint.equivalent(refType, other.refType)) return false;
            return true;
        }

        public ExtenderKey getExtenderKey() {
            return new ExtenderKey(refType, entry.getBytecodeIndexHandler());
        }


    }

    public class ExtenderKey {
        private final JavaRefTypeInstance type;
        private final short handler;

        public ExtenderKey(JavaRefTypeInstance type, short handler) {
            this.type = type;
            this.handler = handler;
        }

        public JavaRefTypeInstance getType() {
            return type;
        }

        public short getHandler() {
            return handler;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExtenderKey that = (ExtenderKey) o;

            if (handler != that.handler) return false;
            if (type != null ? !type.equals(that.type) : that.type != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (int) handler;
            return result;
        }
    }

}
