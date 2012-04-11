package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 18:30
 * To change this template use File | Settings | File Templates.
 */
public class StackEntryHolder {
    private StackEntry stackEntry;

    public StackEntryHolder(StackType stackType) {
        stackEntry = new StackEntry(stackType);
    }

    public void mergeWith(StackEntryHolder other) {
        stackEntry.mergeWith(other.stackEntry);
        other.stackEntry = stackEntry;
    }

    @Override
    public String toString() {
        return stackEntry.toString();
    }

    public StackEntry getStackEntry() {
        return stackEntry;
    }
}
