package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerCommentSource;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class StackEntry {

    private final static AtomicLong sid = new AtomicLong(0);

    private final long id0;
    private final Set<Long> ids = SetFactory.newSet();
    private int artificalSourceCount = 0;
    private final StackSSALabel lValue;
    private long usageCount = 0;
    private final StackType stackType;
    private final InferredJavaType inferredJavaType = new InferredJavaType();

    StackEntry(StackType stackType) {
        id0 = sid.addAndGet(1);
        ids.add(id0);
        this.lValue = new StackSSALabel(id0, this);
        this.stackType = stackType;
    }

    public void incrementUsage() {
        ++usageCount;
    }

    public void decrementUsage() {
        --usageCount;
    }

    public void forceUsageCount(long newCount) {
        usageCount = newCount;
    }

    void mergeWith(StackEntry other, Set<DecompilerComment> comments) {
        if (other.stackType != this.stackType) {
            comments.add(DecompilerComment.UNVERIFIABLE_BYTECODE_BAD_MERGE);
        }
        ids.addAll(other.ids);
        usageCount += other.usageCount;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public int getSourceCount() {
        return ids.size() + artificalSourceCount;
    }

    public void incSourceCount() {
        artificalSourceCount++;
    }

    public void decSourceCount() {
        artificalSourceCount--;
    }

    public List<Long> getSources() {
        return ListFactory.newList(ids);
    }

    public void removeSource(long x) {
        if (!ids.remove(x)) {
            throw new ConfusedCFRException("Attempt to remove non existent id");
        }
    }

    @Override
    public String toString() {
        return "" + id0;
    }

    public StackSSALabel getLValue() {
        return lValue;
    }

    public StackType getType() {
        return stackType;
    }

    public InferredJavaType getInferredJavaType() {
        return inferredJavaType;
    }

    @Override
    public int hashCode() {
        return (int) id0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof StackEntry)) return false;
        return id0 == ((StackEntry) o).id0;
    }
}
