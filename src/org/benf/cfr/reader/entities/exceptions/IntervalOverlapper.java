package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.*;

public class IntervalOverlapper {


    private final NavigableMap<Integer, Set<ExceptionTableEntry>> starts = MapFactory.newTreeMap();
    private final NavigableMap<Integer, Set<ExceptionTableEntry>> ends = MapFactory.newTreeMap();
    
    IntervalOverlapper(List<ExceptionTableEntry> entries) {
        // Should do this in a builder not in a constructor... eww.
        processEntries(entries);
    }

    private void processEntries(List<ExceptionTableEntry> entries) {
        for (ExceptionTableEntry e : entries) {
            processEntry(e);
        }
    }

    //Apache Harmony throws an NPE when calling methods on value set of a blank map
    private static <X> Set<X> razeValues(NavigableMap<?, Set<X>> map) {
        Set<X> res = SetFactory.newSet();
        if (map.isEmpty()) return res;
        for (Set<X> i : map.values()) {
            res.addAll(i);
        }
        return res;
    }

    /* This is hideous - O(N^2). */
    private void processEntry(ExceptionTableEntry e) {
        final int from = e.getBytecodeIndexFrom();
        final int to = e.getBytecodeIndexTo();

        // TODO : This won't ignore 0-2 if we already have 0-7

        // Get the set that started before the start and end before the end.
        NavigableMap<Integer, Set<ExceptionTableEntry>> startedBeforeStart = starts.headMap(from, false);
        NavigableMap<Integer, Set<ExceptionTableEntry>> endsBeforeEnd = ends.headMap(to, false);
        NavigableMap<Integer, Set<ExceptionTableEntry>> endsInside = endsBeforeEnd.tailMap(from, false);
        // Anything that's in the values of endsInside, AND the values of startedBeforeStart, is 'bad'.
        // We'll remove them from BOTH starts and ends, split them up, and then add them back again.
        Set<ExceptionTableEntry> overlapStartsBefore = razeValues(endsInside);
        overlapStartsBefore.retainAll(razeValues(startedBeforeStart));

        NavigableMap<Integer, Set<ExceptionTableEntry>> endsAfterEnd = ends.tailMap(to, false);
        NavigableMap<Integer, Set<ExceptionTableEntry>> startedAfterStart = starts.tailMap(from, false);
        NavigableMap<Integer, Set<ExceptionTableEntry>> startsInside = startedAfterStart.headMap(to, false);

        Set<ExceptionTableEntry> overlapEndsAfter = razeValues(startsInside);
        overlapEndsAfter.retainAll(razeValues(endsAfterEnd));

        if (overlapEndsAfter.isEmpty() && overlapStartsBefore.isEmpty()) {
            addEntry(e);
            return;
        }

        int currentFrom;
        int currentTo;
        int remainingBlockStart = from;
        int remainingBlockTo = to;
        List<ExceptionTableEntry> output = ListFactory.newList();
        // by definition, startsbefore and endsafter won't overlap.
        if (!overlapStartsBefore.isEmpty()) {
            Set<Integer> blockEnds = new TreeSet<Integer>();
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                blockEnds.add(e2.getBytecodeIndexTo());
                starts.get(e2.getBytecodeIndexFrom()).remove(e2);
                ends.get(e2.getBytecodeIndexTo()).remove(e2);
            }
            // Divide e into start->ends[0], ends[0]->ends[1], ends[1] -> ends[2], etc.
            currentFrom = from;
            for (Integer end : blockEnds) {
                ExceptionTableEntry out = e.copyWithRange(currentFrom,(end));
                addEntry(out);
                output.add(out);
                currentFrom = (end);
            }
            remainingBlockStart = currentFrom;
            blockEnds.add((from));
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                currentFrom = e2.getBytecodeIndexFrom();
                for (Integer end : blockEnds) {
                    if (end > e2.getBytecodeIndexTo()) break;
                    ExceptionTableEntry out = e2.copyWithRange(currentFrom, (end));
                    addEntry(out);
                    output.add(out);
                    currentFrom = (end);
                }
            }

        }

        if (!overlapEndsAfter.isEmpty()) {
            Set<Integer> blockStarts = new TreeSet<Integer>();
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                blockStarts.add(e2.getBytecodeIndexFrom());
                starts.get(e2.getBytecodeIndexFrom()).remove(e2);
                ends.get(e2.getBytecodeIndexTo()).remove(e2);
            }
            List<Integer> revBlockStarts = ListFactory.newList(blockStarts);
            currentTo = to;
            for (int x = revBlockStarts.size() - 1; x >= 0; --x) {
                Integer start = revBlockStarts.get(x);
                ExceptionTableEntry out = e.copyWithRange(start, currentTo);
                addEntry(out);
                output.add(out);
                currentTo = (start);
            }
            remainingBlockTo = currentTo;
            revBlockStarts.add((to));
            for (ExceptionTableEntry e2 : overlapStartsBefore) {
                currentTo = e2.getBytecodeIndexTo();
                for (int x = revBlockStarts.size() - 1; x >= 0; --x) {
                    Integer start = revBlockStarts.get(x);
                    if (start <= e2.getBytecodeIndexFrom()) break;
                    ExceptionTableEntry out = e.copyWithRange(start, currentTo);
                    addEntry(out);
                    output.add(out);
                    currentTo = (start);
                }
            }
        }

        ExceptionTableEntry out = e.copyWithRange(remainingBlockStart, remainingBlockTo);
        addEntry(out);
        output.add(out);
    }

    private void addEntry(ExceptionTableEntry e) {
        add(starts, e.getBytecodeIndexFrom(), e);
        add(ends, e.getBytecodeIndexTo(), e);
    }

    private <A, B> void add(NavigableMap<A, Set<B>> m, A k, B v) {
        Set<B> b = m.get(k);
        if (b == null) {
            b = SetFactory.newSet();
            m.put(k, b);
        }
        b.add(v);
    }

    public List<ExceptionTableEntry> getExceptions() {
        return ListFactory.newList(razeValues(starts));
    }
}
