package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;

public class StackEntryHolder {
    private StackEntry stackEntry;

    public StackEntryHolder(StackType stackType) {
        stackEntry = new StackEntry(stackType);
    }

    public void mergeWith(StackEntryHolder other) {
        if (stackEntry.mergeWith(other.stackEntry)) {
            other.stackEntry = stackEntry;
        }
    }

    @Override
    public String toString() {
        return stackEntry.toString();
    }

    public StackEntry getStackEntry() {
        return stackEntry;
    }
}
