package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.util.ConfusedCFRException;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 13/05/2011
 * Time: 06:59
 * To change this template use File | Settings | File Templates.
 */
public class StackSim {
    private final StackSim parent;
    private final StackEntryHolder stackEntryHolder;
    private final long depth;

    public StackSim() {
        this.depth = 0;
        this.parent = null;
        this.stackEntryHolder = null;
    }

    private StackSim(StackSim parent, StackType stackType) {
        this.parent = parent;
        this.depth = parent.depth + 1;
        this.stackEntryHolder = new StackEntryHolder(stackType);
    }

    public StackEntry getEntry(int depth) {
        StackSim thisSim = this;
        while (depth > 0) {
            thisSim = thisSim.getParent();
            depth--;
        }
        return thisSim.stackEntryHolder.getStackEntry();
    }

    public long getDepth() {
        return depth;
    }

    public StackSim getChange(StackDelta delta, List<StackEntryHolder> consumed, List<StackEntryHolder> produced) {
        if (delta.isNoOp()) {
            return this;
        }
        StackSim thisSim = this;
        StackTypes consumedStack = delta.getConsumed();
        for (StackType stackType : consumedStack) {
            consumed.add(thisSim.stackEntryHolder);
            thisSim = thisSim.getParent();
        }
        StackTypes producedStack = delta.getProduced();
        for (StackType stackType : producedStack) {
            thisSim = new StackSim(thisSim, stackType);
            produced.add(thisSim.stackEntryHolder);
        }
        return thisSim;
    }

    private StackSim getParent() {
        if (parent == null) {
            throw new ConfusedCFRException("Stack underflow");
        }
        return parent;
    }
}
