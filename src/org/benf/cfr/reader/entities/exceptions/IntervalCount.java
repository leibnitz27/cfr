package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;

import java.util.*;

public class IntervalCount {
    private final TreeMap<Short, Boolean> op = MapFactory.newTreeMap();

    // Maintains a half open interval
    // x, true ... something started at x.
    // x, false .. something ended including x-1.
    public Pair<Short, Short> generateNonIntersection(Short from, Short to) {
        if (to < from) return null;

        Map.Entry<Short, Boolean> prevEntry = op.floorEntry(from);
        Boolean previous = prevEntry == null ? null : prevEntry.getValue();
        boolean braOutside = previous == null || (!previous);

        if (braOutside) {
            op.put(from, true);
        } else {
            from = prevEntry.getKey();
            /*
             * If the new exception entry is entirely subsumed within from -> next ket, then we have
             * a totally redundant exception entry.
             */
            Map.Entry<Short, Boolean> nextEntry = op.ceilingEntry((short) (from + 1));
            if (nextEntry == null) {
                throw new IllegalStateException("Internal exception pattern invalid");
            }
            if (!nextEntry.getValue()) { // next is a ket
                if (nextEntry.getKey() >= to) {
                    // Entirely subsumed within previous entry, redundant.
                    return null;
                }
            }
        }

        NavigableMap<Short, Boolean> afterMap = op.tailMap(from, false);

        Set<Map.Entry<Short, Boolean>> afterSet = afterMap.entrySet();
        Iterator<Map.Entry<Short, Boolean>> afterIter = afterSet.iterator();
        while (afterIter.hasNext()) {
            Map.Entry<Short, Boolean> next = afterIter.next();
            Short end = next.getKey();
            boolean isKet = Boolean.FALSE == next.getValue();
            if (end > to) {
                if (isKet) {
                    // Fine.  We'll just extend the range of the newer one.
                    return Pair.make(from, end);
                }
                // Then we'll add another ket, and place it before end.
                op.put(to, false);
                return Pair.make(from, to);
            } else if (end.equals(to)) {
                if (isKet) {
                    // Fine, nothing to do.
                    return Pair.make(from, end);
                }
                // We remove this bra, and coalesce the ranges.
                afterIter.remove();
                return Pair.make(from, to);
            }

            // end < to.  This means that a more important exception is inner.
            // This is pretty common!
            // Remove the previous one.
            afterIter.remove();
        }
        // Walked off then end?
        op.put(to, false);
        return Pair.make(from, to);
    }
}
