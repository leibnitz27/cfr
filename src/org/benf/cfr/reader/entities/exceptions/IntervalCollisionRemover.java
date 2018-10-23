package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Unlike the overlapper, which has to generate exceptions from overlapping pairs, this acts only
 * to filter out impossibilities - because
 * <p/>
 * 0->5 (Throwable) -> 100
 * 0->5 (Throwable) -> 0    <-- not illegal, but will never be seen...
 */
public class IntervalCollisionRemover {
    private final TreeMap<Integer, Boolean> covered = new TreeMap<Integer, Boolean>();

    public List<ClosedIdxExceptionEntry> removeIllegals(ClosedIdxExceptionEntry e) {
        List<ClosedIdxExceptionEntry> res = ListFactory.newList();

        int start = e.getStart();
        int end = e.getEnd();

        do {
            Map.Entry<Integer, Boolean> before = covered.floorEntry(start);
            if (before == null || !before.getValue()) {
                if (before == null || before.getKey() < start) {
                    // nothing, or gap before us.
                    covered.put(start, true);
                } else {
                    // We were on the one after a previous.
                    covered.remove(start);
                }
                // Nothing started here.
                // Find next start (if there is one)
                Map.Entry<Integer, Boolean> nextStart = covered.ceilingEntry(start + 1);
                if (nextStart == null || nextStart.getKey() > end) {
                    covered.put(end + 1, false);
                    res.add(e.withRange(start, end));
                    return res;
                }
                // We can emit from start -> nextStart-1, add our
                // start, and remove nextStart.  Then set our start to be nextStart, and loop.
                covered.remove(nextStart.getKey());
                res.add(e.withRange(start, nextStart.getKey() - 1));
                start = nextStart.getKey();
            } else {
                // Already covered at start.
                if (before.getKey().equals(start)) ++start;
                // The next covered has to be an End, by definition.
                Map.Entry<Integer, Boolean> nextEnd = covered.ceilingEntry(start);
                int nextEndIdx = nextEnd.getKey();
                if (nextEnd.getValue()) throw new IllegalStateException();
                // If we've gone past the end of the new interval, finish.
                if (nextEndIdx > end) return res;
                start = nextEndIdx;
            }
        } while (true);
    }
}
