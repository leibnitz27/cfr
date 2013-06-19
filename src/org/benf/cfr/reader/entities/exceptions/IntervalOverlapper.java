package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/06/2013
 * Time: 06:15
 */
public class IntervalOverlapper {


    private final NavigableMap<Short, Set<ExceptionTableEntry>> starts = MapFactory.newTreeMap();
    private final NavigableMap<Short, Set<ExceptionTableEntry>> ends = MapFactory.newTreeMap();


    public IntervalOverlapper(List<ExceptionTableEntry> entries) {
        // Should do this in a builder not in a constructor... eww.
        processEntries(entries);
    }

    private void processEntries(List<ExceptionTableEntry> entries) {
        for (ExceptionTableEntry e : entries) {
            processEntry(e);
        }
    }

    private static <X> Set<X> raze(Collection<Set<X>> in) {
        Set<X> res = SetFactory.newSet();
        for (Set<X> i : in) {
            res.addAll(i);
        }
        return res;
    }

    /* This is hideous - O(N^2). */
    private void processEntry(ExceptionTableEntry e) {
        final short from = e.getBytecodeIndexFrom();
        final short to = e.getBytecodeIndexTo();

        // Get the set that started before the start and end before the end.
        NavigableMap<Short, Set<ExceptionTableEntry>> startedBeforeStart = starts.headMap(from, false);
        NavigableMap<Short, Set<ExceptionTableEntry>> endsBeforeEnd = ends.headMap(to, false);
        NavigableMap<Short, Set<ExceptionTableEntry>> endsInside = endsBeforeEnd.tailMap(from, false);
        // Anything that's in the values of endsInside, AND the values of startedBeforeStart, is 'bad'.
        // We'll remove them from BOTH starts and ends, split them up, and then add them back again.
        Set<ExceptionTableEntry> overlapStartsBefore = raze(endsInside.values());
        overlapStartsBefore.retainAll(raze(startedBeforeStart.values()));

        NavigableMap<Short, Set<ExceptionTableEntry>> endsAfterEnd = ends.tailMap(to, false);
        NavigableMap<Short, Set<ExceptionTableEntry>> startedAfterStart = starts.tailMap(from, false);
        NavigableMap<Short, Set<ExceptionTableEntry>> startsInside = startedAfterStart.headMap(to, false);

        Set<ExceptionTableEntry> overlapEndsAfter = raze(startsInside.values());
        overlapEndsAfter.retainAll(raze(endsAfterEnd.values()));

        if (overlapEndsAfter.isEmpty() && overlapStartsBefore.isEmpty()) {
            addEntry(e);
            return;
        }

        short currentFrom;
        short currentTo;
        short remainingBlockStart = from;
        short remainingBlockTo = to;
        List<ExceptionTableEntry> output = ListFactory.newList();
        // by definition, startsbefore and endsafter won't overlap.
        if (!overlapStartsBefore.isEmpty()) {
            Set<Short> blockEnds = new TreeSet<Short>();
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                blockEnds.add(e2.getBytecodeIndexTo());
                starts.get(e2.getBytecodeIndexFrom()).remove(e2);
                ends.get(e2.getBytecodeIndexTo()).remove(e2);
            }
            // Divide e into start->ends[0], ends[0]->ends[1], ends[1] -> ends[2], etc.
            currentFrom = from;
            for (Short end : blockEnds) {
                ExceptionTableEntry out = e.copyWithRange(currentFrom, (short) (end));
                addEntry(out);
                output.add(out);
                currentFrom = (short) (end);
            }
            remainingBlockStart = currentFrom;
            blockEnds.add((short) (from));
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                currentFrom = e2.getBytecodeIndexFrom();
                for (Short end : blockEnds) {
                    if (end > e2.getBytecodeIndexTo()) break;
                    ExceptionTableEntry out = e2.copyWithRange(currentFrom, (short) (end));
                    addEntry(out);
                    output.add(out);
                    currentFrom = (short) (end);
                }
            }

        }

        if (!overlapEndsAfter.isEmpty()) {
            Set<Short> blockStarts = new TreeSet<Short>();
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                blockStarts.add(e2.getBytecodeIndexFrom());
                starts.get(e2.getBytecodeIndexFrom()).remove(e2);
                ends.get(e2.getBytecodeIndexTo()).remove(e2);
            }
            List<Short> revBlockStarts = ListFactory.newList(blockStarts);
            currentTo = to;
            for (int x = revBlockStarts.size() - 1; x >= 0; --x) {
                Short start = revBlockStarts.get(x);
                ExceptionTableEntry out = e.copyWithRange(start, currentTo);
                addEntry(out);
                output.add(out);
                currentTo = (short) (start);
            }
            remainingBlockTo = currentTo;
            revBlockStarts.add((short) (to));
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                currentTo = e2.getBytecodeIndexTo();
                for (int x = revBlockStarts.size() - 1; x >= 0; --x) {
                    Short start = revBlockStarts.get(x);
                    if (start <= e2.getBytecodeIndexFrom()) break;
                    ExceptionTableEntry out = e.copyWithRange(start, currentTo);
                    addEntry(out);
                    output.add(out);
                    currentTo = (short) (start);
                }
            }
        }

        ExceptionTableEntry out = e.copyWithRange(remainingBlockStart, remainingBlockTo);
        addEntry(out);
        output.add(out);
    }

    void addEntry(ExceptionTableEntry e) {
        add(starts, e.getBytecodeIndexFrom(), e);
        add(ends, e.getBytecodeIndexTo(), e);
    }

    <A, B> void add(NavigableMap<A, Set<B>> m, A k, B v) {
        Set<B> b = m.get(k);
        if (b == null) {
            b = SetFactory.newSet();
            m.put(k, b);
        }
        b.add(v);
    }

    public List<ExceptionTableEntry> getExceptions() {
        return ListFactory.newList(raze(starts.values()));
    }
}
