package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 30/03/2012
 * Time: 06:51
 * To change this template use File | Settings | File Templates.
 */
public class ExceptionAggregator {

    private final List<ExceptionGroup> exceptionsByRange = ListFactory.newList();

    private static class CompareExceptionTablesByRange implements Comparator<ExceptionTableEntry> {
        @Override
        public int compare(ExceptionTableEntry exceptionTableEntry, ExceptionTableEntry exceptionTableEntry1) {
            int res = exceptionTableEntry.getBytecodeIndexFrom() - exceptionTableEntry1.getBytecodeIndexFrom();
            if (res != 0) return res;
            return exceptionTableEntry.getBytecodeIndexTo() - exceptionTableEntry1.getBytecodeIndexTo();
        }
    }

    private static class ByTarget {
        private final List<ExceptionTableEntry> entries;

        public ByTarget(List<ExceptionTableEntry> entries) {
            this.entries = entries;
        }

        public Collection<ExceptionTableEntry> getAggregated() {
            Collections.sort(this.entries, new CompareExceptionTablesByRange());
            /* If two entries are contiguous, they can be merged 
             * If they're 'almost' contiguous, but point to the same range? ........ don't know.
             */
            List<ExceptionTableEntry> res = ListFactory.newList();
            ExceptionTableEntry held = null;
            for (ExceptionTableEntry entry : this.entries) {
                if (held == null) {
                    held = entry;
                } else {
                    // TODO - shouldn't be using bytecode indices unless we can account for instruction length?
                    // TODO - depends if the end is the start of the last opcode, or the end.
                    if (held.getBytecodeIndexTo() == entry.getBytecodeIndexFrom()) {
                        held = held.aggregateWith(entry);
                    } else if (held.getBytecodeIndexFrom() == entry.getBytecodeIndexFrom() &&
                            held.getBytecodeIndexTo() <= entry.getBytecodeIndexTo()) {
                        held = entry;
                    } else {
                        res.add(held);
                        held = entry;
                    }
                }
            }
            if (held != null) res.add(held);
            return res;
        }
    }

    private static class ValidException implements Predicate<ExceptionTableEntry> {
        @Override
        public boolean test(ExceptionTableEntry in) {
            return (in.getBytecodeIndexFrom() != in.getBytecodeIndexHandler());
        }
    }

    /* Raw exceptions are just start -> last+1 lists.  There's no (REF?) requirement that they be non overlapping, so
    * I guess a compiler could have a,b a2, b2 where a < a2, b > a2 < b2... (eww).
    * In that case, we should split the exception regime into non-overlapping sections.
    */
    public ExceptionAggregator(List<ExceptionTableEntry> rawExceptions, BlockIdentifierFactory blockIdentifierFactory,
                               Map<Integer, Integer> lutByOffset,
                               List<Op01WithProcessedDataAndByteJumps> instrs,
                               ConstantPool cp) {

        rawExceptions = Functional.filter(rawExceptions, new ValidException());
        if (rawExceptions.isEmpty()) return;

        /*
         * Extend an exception which terminates at a return.
         * Remember exception tables are half closed [0,1) == just covers 0.
         */
        List<ExceptionTableEntry> extended = ListFactory.newList();
        for (ExceptionTableEntry exceptionTableEntry : rawExceptions) {

            Integer tgtIdx = lutByOffset.get((int) exceptionTableEntry.getBytecodeIndexTo());
            if (tgtIdx != null) {
                Op01WithProcessedDataAndByteJumps op01 = instrs.get(tgtIdx);
                JVMInstr instr = op01.getJVMInstr();
                switch (instr) {
                    case RETURN:
                    case ARETURN:
                    case DRETURN:
                    case IRETURN:
                    case LRETURN:
                    case FRETURN:
                        exceptionTableEntry = exceptionTableEntry.copyWithRange(exceptionTableEntry.getBytecodeIndexFrom(),
                                (short) (exceptionTableEntry.getBytecodeIndexTo() + op01.getInstructionLength()));
                        break;
                }
            }
            extended.add(exceptionTableEntry);
        }
        rawExceptions = extended;

        /*
         * If an exception table entry for a type X OVERLAPS an entry for a type X, but has a lower priority, then
         * it is truncated.  This probably indicates obfuscation.
         */

        // Need to build up an interval tree for EACH exception handler type
        Map<Short, List<ExceptionTableEntry>> grouped = Functional.groupToMapBy(rawExceptions, new UnaryFunction<ExceptionTableEntry, Short>() {
            @Override
            public Short invoke(ExceptionTableEntry arg) {
                return arg.getCatchType();
            }
        });

        List<ExceptionTableEntry> processedExceptions = ListFactory.newList(rawExceptions.size());
        for (List<ExceptionTableEntry> list : grouped.values()) {
            IntervalCount intervalCount = new IntervalCount();
            for (ExceptionTableEntry e : list) {
                Pair<Short, Short> res = intervalCount.generateNonIntersection(e.getBytecodeIndexFrom(), e.getBytecodeIndexTo());
                if (res == null) continue;
                processedExceptions.add(new ExceptionTableEntry(res.getFirst(), res.getSecond(), e.getBytecodeIndexHandler(), e.getCatchType(), e.getPriority()));
            }
        }
        rawExceptions = processedExceptions;

        /* 
         * Try and aggregate exceptions for the same object which jump to the same target.
         */
        Collection<ByTarget> byTargetList = Functional.groupBy(rawExceptions, new Comparator<ExceptionTableEntry>() {
                    @Override
                    public int compare(ExceptionTableEntry exceptionTableEntry, ExceptionTableEntry exceptionTableEntry1) {
                        int hd = exceptionTableEntry.getBytecodeIndexHandler() - exceptionTableEntry1.getBytecodeIndexHandler();
                        if (hd != 0) return hd;
                        return exceptionTableEntry.getCatchType() - exceptionTableEntry1.getCatchType();
                    }
                }, new UnaryFunction<List<ExceptionTableEntry>, ByTarget>() {
                    @Override
                    public ByTarget invoke(List<ExceptionTableEntry> arg) {
                        return new ByTarget(arg);
                    }
                }
        );

        rawExceptions.clear();
        /* 
         * Each of these is now lists which point to the same handler+type.
         */
        for (ByTarget byTarget : byTargetList) {
            rawExceptions.addAll(byTarget.getAggregated());
        }

        /*
         * But if two different exceptions actually overlap, then we've either got obfuscation or hand coded?
         * (or some interesting transformation).
         */
        IntervalOverlapper intervalOverlapper = new IntervalOverlapper(rawExceptions);
        rawExceptions = intervalOverlapper.getExceptions();

        Collections.sort(rawExceptions);

        CompareExceptionTablesByRange compareExceptionTablesByStart = new CompareExceptionTablesByRange();
        ExceptionTableEntry prev = null;
        ExceptionGroup currentGroup = null;
        for (ExceptionTableEntry e : rawExceptions) {
            if (prev == null || compareExceptionTablesByStart.compare(e, prev) != 0) {
                currentGroup = new ExceptionGroup(e.getBytecodeIndexFrom(), blockIdentifierFactory.getNextBlockIdentifier(BlockType.TRYBLOCK), cp);
                exceptionsByRange.add(currentGroup);
                prev = e;
            }
            currentGroup.add(e);
        }
    }

    public List<ExceptionGroup> getExceptionsGroups() {
        return exceptionsByRange;
    }
}
