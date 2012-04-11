package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 12/03/2012
 * Time: 17:56
 * To change this template use File | Settings | File Templates.
 */
public class StackDelta {
    private final StackTypes consumed;
    private final StackTypes produced;

    public StackDelta(StackTypes consumed, StackTypes produced) {
        if (consumed == null || produced == null) {
            throw new ConfusedCFRException("Must not have null stackTypes");
        }
        this.consumed = consumed;
        this.produced = produced;
    }

    public boolean isNoOp() {
        return consumed.isEmpty() && produced.isEmpty();
    }

    public StackTypes getConsumed() {
        return consumed;
    }

    public StackTypes getProduced() {
        return produced;
    }

    public long getChange() {
        return produced.size() - consumed.size();
    }

    @Override
    public String toString() {
        return "Consumes " + consumed + ", Produces " + produced;
    }
}
