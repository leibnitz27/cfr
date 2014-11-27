package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;

import java.util.List;

public class LinearScannedBlock {
    private Op03SimpleStatement first;
    private Op03SimpleStatement last;
    private int idxFirst;
    private int idxLast;

    public LinearScannedBlock(Op03SimpleStatement first, Op03SimpleStatement last, int idxFirst, int idxLast) {
        this.first = first;
        this.last = last;
        this.idxFirst = idxFirst;
        this.idxLast = idxLast;
    }

    public Op03SimpleStatement getFirst() {
        return first;
    }

    public Op03SimpleStatement getLast() {
        return last;
    }

    public int getIdxFirst() {
        return idxFirst;
    }

    public int getIdxLast() {
        return idxLast;
    }

    public boolean isAfter(LinearScannedBlock other) {
        return idxFirst > other.idxLast;
    }

    public boolean immediatelyFollows(LinearScannedBlock other) {
        return idxFirst == other.idxLast+1;
    }

    /*
     * We know the start and end statements are still correct, but they might
     * have moved - if they have, get their new indices.
     */
    public void reindex(List<Op03SimpleStatement> in) {
        if (in.get(idxFirst) != first) idxFirst = in.indexOf(first);
        if (in.get(idxLast) != last) idxLast = in.indexOf(last);
    }
}
