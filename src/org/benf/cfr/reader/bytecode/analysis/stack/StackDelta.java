package org.benf.cfr.reader.bytecode.analysis.stack;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 12/03/2012
 * Time: 17:56
 * To change this template use File | Settings | File Templates.
 */
public class StackDelta {
    private final long consumed;
    private final long produced;
    public StackDelta(long consumed, long produced) {
        this.consumed = consumed;
        this.produced = produced;
    }

    public boolean isNoOp() {
        return consumed == 0 && produced == 0;
    }

    public long getConsumed() {
        return consumed;
    }

    public long getProduced() {
        return produced;
    }

    public long getChange() {
        return produced-consumed;
    }
    
    @Override
    public String toString() {
        return "Consumes " + consumed + ", Produces " + produced;
    }
}
