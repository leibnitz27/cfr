package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Collection;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 13/03/2012
 * Time: 18:35
 * To change this template use File | Settings | File Templates.
 */
public class StackEntry {

    private static long sid = 0;

    private final long id0;
    private final Set<Long> ids = SetFactory.newSet();
    private final StackSSALabel lValue;
    private long usageCount = 0;
    private final StackType stackType;

    public StackEntry(StackType stackType) {
        id0 = sid++;
        ids.add(id0);
        this.lValue = new StackSSALabel(id0, this);
        this.stackType = stackType;
    }

    public void incrementUsage() {
        usageCount++;
    }

    public void decrementUsage() {
        usageCount--;
    }

    public void mergeWith(StackEntry other) {
        if (other.stackType != this.stackType) {
            throw new ConfusedCFRException("Trying to merge different stackTypes");
        }
        ids.addAll(other.ids);
        usageCount += other.usageCount;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public int getSourceCount() {
        return ids.size();
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
}
