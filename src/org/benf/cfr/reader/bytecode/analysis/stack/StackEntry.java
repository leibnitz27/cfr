package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.List;
import java.util.Set;

public class StackEntry {

    private static long sid = 0;

    private final long id0;
    private final Set<Long> ids = SetFactory.newSet();
    private int artificalSourceCount = 0;
    private final StackSSALabel lValue;
    private long usageCount = 0;
    private final StackType stackType;
    private final InferredJavaType inferredJavaType = new InferredJavaType();

    public StackEntry(StackType stackType) {
        id0 = sid++;
        ids.add(id0);
        this.lValue = new StackSSALabel(id0, this);
        this.stackType = stackType;
    }

    public long incrementUsage() {
        return ++usageCount;
    }

    public long decrementUsage() {
        return (--usageCount);
    }

    public long forceUsageCount(long newCount) {
        usageCount = newCount;
        return usageCount;
    }

    public boolean mergeWith(StackEntry other) {
        if (other.stackType != this.stackType) {
            return false;
//            throw new ConfusedCFRException("Trying to merge different stackTypes " + stackType + " vs " + other.stackType + " [" + id0 + "/" + other.id0 + "]");
        }
        ids.addAll(other.ids);
        usageCount += other.usageCount;
        return true;
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
