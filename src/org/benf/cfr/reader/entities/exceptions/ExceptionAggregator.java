package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.*;

public class ExceptionAggregator {

    private final List<ExceptionGroup> exceptionsByRange = ListFactory.newList();
    private final Method method;
    private final Map<Integer, Integer> lutByOffset;
    private final Map<Integer, Integer> lutByIdx;
    private final List<Op01WithProcessedDataAndByteJumps> instrs;
    private final Options options;
    private final boolean aggressivePrune;
    private final boolean aggressiveAggregate;


    private static class CompareExceptionTablesByRange implements Comparator<ExceptionTableEntry> {
        @Override
        public int compare(ExceptionTableEntry exceptionTableEntry, ExceptionTableEntry exceptionTableEntry1) {
            int res = exceptionTableEntry.getBytecodeIndexFrom() - exceptionTableEntry1.getBytecodeIndexFrom();
            if (res != 0) return res;
            return exceptionTableEntry.getBytecodeIndexTo() - exceptionTableEntry1.getBytecodeIndexTo();
        }
    }

    private class ByTarget {
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
                    } else if (held.getBytecodeIndexFrom() < entry.getBytecodeIndexFrom() &&
                            entry.getBytecodeIndexFrom() < held.getBytecodeIndexTo() &&
                            entry.getBytecodeIndexTo() > held.getBytecodeIndexTo()) {
                        held = held.aggregateWithLenient(entry);
                    } else if (aggressiveAggregate && canExtendTo(held, entry)) {
                        held = held.aggregateWithLenient(entry);
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

    /*
     * We have a range a[1,4] b[6, 7].
     *
     * If a can be extended to cover 1-6, then a & b can be combined.
     * (remember, exception ranges are half open, 1-4 covers 1 UNTIL 4, not incl).
     *
     * Some instructions can be guaranteed not to throw, they can be extended over.
     */
    private boolean canExtendTo(ExceptionTableEntry a, ExceptionTableEntry b) {
        final int startNext = b.getBytecodeIndexFrom();
        int current = a.getBytecodeIndexTo();
        if (current > startNext) return false;

        while (current < startNext) {
            Integer idx = lutByOffset.get(current);
            if (idx == null) return false;
            Op01WithProcessedDataAndByteJumps op = instrs.get(idx);
            JVMInstr instr = op.getJVMInstr();
            if (instr.isNoThrow()) {
                current += op.getInstructionLength();
            } else if (aggressivePrune) {
                switch (instr) {
                    // Getstatic CAN throw, but some code will separate exception blocks around it, just to be
                    // awkward.
                    case GETSTATIC:
                        current += op.getInstructionLength();
                        break;
                    default:
                        return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }


    // Note - we deliberately don't use instr.isNoThrow here, that leads to over eager expansion into exception handlers!
    private static int canExpandTryBy(int idx, List<Op01WithProcessedDataAndByteJumps> statements) {
        Op01WithProcessedDataAndByteJumps op = statements.get(idx);
        JVMInstr instr = op.getJVMInstr();
        switch (instr) {
            case GOTO:
            case GOTO_W:
            case RETURN:
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case DRETURN:
            case FRETURN: {
                return op.getInstructionLength();
            }
            case ALOAD:
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3: {
                Op01WithProcessedDataAndByteJumps op2 = statements.get(idx + 1);
                if (op2.getJVMInstr() == JVMInstr.MONITOREXIT)
                    return op.getInstructionLength() + op2.getInstructionLength();
                break;
            }
        }
        return 0;
    }

    /* Raw exceptions are just start -> last+1 lists.  There's no (REF?) requirement that they be non overlapping, so
    * I guess a compiler could have a,b a2, b2 where a < a2, b > a2 < b2... (eww).
    * In that case, we should split the exception regime into non-overlapping sections.
    */
    public ExceptionAggregator(List<ExceptionTableEntry> rawExceptions, BlockIdentifierFactory blockIdentifierFactory,
                               final Map<Integer, Integer> lutByOffset,
                               final Map<Integer, Integer> lutByIdx,
                               List<Op01WithProcessedDataAndByteJumps> instrs,
                               final Options options,
                               final ConstantPool cp,
                               final Method method) {

        this.method = method;
        this.lutByIdx = lutByIdx;
        this.lutByOffset = lutByOffset;
        this.instrs = instrs;
        this.options = options;
        this.aggressivePrune = options.getOption(OptionsImpl.FORCE_PRUNE_EXCEPTIONS) == Troolean.TRUE;
        this.aggressiveAggregate = options.getOption(OptionsImpl.FORCE_AGGRESSIVE_EXCEPTION_AGG) == Troolean.TRUE;

        rawExceptions = Functional.filter(rawExceptions, new ValidException());
        if (rawExceptions.isEmpty()) return;

//        List<ClosedIdxExceptionEntry> convExceptions = Functional.map(rawExceptions, new UnaryFunction<ExceptionTableEntry, ClosedIdxExceptionEntry>() {
//            @Override
//            public ClosedIdxExceptionEntry invoke(ExceptionTableEntry arg) {
//                return new ClosedIdxExceptionEntry(
//                    lutByOffset.get((int)arg.getBytecodeIndexFrom()),
//                    lutByOffset.get((int)arg.getBytecodeIndexTo()),
//                    lutByOffset.get((int)arg.getBytecodeIndexHandler()),
//                    arg.getCatchType(),
//                    arg.getPriority(),
//                    arg.getCatchType(cp)
//                );
//            }
//        });
//
//        /*
//         * Before we start, go through and weed out totally invalid exceptions - ones that could never get
//         * caught, because they're masked by earlier exceptions.  This would be an easy way to confuse naive
//         * decompilation....
//         */
//        Map < JavaRefTypeInstance, IntervalCollisionRemover > collisionRemoverMap = MapFactory.newLazyMap(new UnaryFunction<JavaRefTypeInstance, IntervalCollisionRemover>() {
//            @Override
//            public IntervalCollisionRemover invoke(JavaRefTypeInstance arg) {
//                return new IntervalCollisionRemover();
//            }
//        });
//        List<ClosedIdxExceptionEntry> collisionRemoved = ListFactory.newList();
//        for (ClosedIdxExceptionEntry e : convExceptions) {
//            collisionRemoved.addAll(collisionRemoverMap.get(e.getCatchRefType()).removeIllegals(e));
//        }
//        convExceptions = collisionRemoved;
//
//
//        /*
//         * Now, convert back to raw exceptions.  I'd rather be doing this later..... (or not at all...)
//         */
//        rawExceptions = Functional.map(convExceptions, new UnaryFunction<ClosedIdxExceptionEntry, ExceptionTableEntry>() {
//            @Override
//            public ExceptionTableEntry invoke(ClosedIdxExceptionEntry arg) {
//                return arg.convertToRaw(lutByIdx);
//            }
//        });
//

        // Todo : Use ConvExceptions, not RawExceptions.
        /*
         * Extend an exception which terminates at a return.
         * Remember exception tables are half closed [0,1) == just covers 0.
         */
        List<ExceptionTableEntry> extended = ListFactory.newList();
        for (ExceptionTableEntry exceptionTableEntry : rawExceptions) {

            ExceptionTableEntry exceptionTableEntryOrig = exceptionTableEntry;

            int indexTo = (int) exceptionTableEntry.getBytecodeIndexTo();

            do {
                exceptionTableEntryOrig = exceptionTableEntry;
                Integer tgtIdx = lutByOffset.get(indexTo);
                if (tgtIdx != null) {

                    // See if the last statement is a direct return, which could be pushed in.  If so, expand try block.
                    int offset = canExpandTryBy(tgtIdx, instrs);
                    if (offset != 0) {
                        exceptionTableEntry = exceptionTableEntry.copyWithRange(exceptionTableEntry.getBytecodeIndexFrom(),
                                (short) (exceptionTableEntry.getBytecodeIndexTo() + offset));
                    }
                    indexTo += offset;
                }

            } while (exceptionTableEntry != exceptionTableEntryOrig);

            /*
             * But now, we shrink it to make sure that it doesn't overlap the catch block.
             * This will break some of the nastier exception obfuscations I can think of :(
             */
            int handlerIndex = exceptionTableEntry.getBytecodeIndexHandler();
            indexTo = exceptionTableEntry.getBytecodeIndexTo();
            int indexFrom = exceptionTableEntry.getBytecodeIndexFrom();
            if (indexFrom < handlerIndex &&
                    indexTo >= handlerIndex) {
                exceptionTableEntry = exceptionTableEntry.copyWithRange((short) indexFrom, (short) handlerIndex);
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
                short from = e.getBytecodeIndexFrom();
                short to = e.getBytecodeIndexTo();
                Pair<Short, Short> res = intervalCount.generateNonIntersection(from, to);
                if (res == null) continue;
                processedExceptions.add(new ExceptionTableEntry(res.getFirst(), res.getSecond(), e.getBytecodeIndexHandler(), e.getCatchType(), e.getPriority()));
            }
        }

        /* 
         * Try and aggregate exceptions for the same object which jump to the same target.
         */
        Collection<ByTarget> byTargetList = Functional.groupBy(processedExceptions, new Comparator<ExceptionTableEntry>() {
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

        rawExceptions = ListFactory.newList();

        /*
         * If there are two exceptions which both vector to the same target,
         *
         * A,B  [e1] ->   X
         * G,H  [e1] ->   X
         *
         * but there is another exception in the range of the EARLIER one which
         * vectors to the later one
         *
         * A,B  [e2] ->   G
         *
         * Then we make the (probably dodgy) assumption that the first exceptions
         * actually are one range.
         *
         */
        Map<Short, ByTarget> byTargetMap = MapFactory.newMap();
        for (ByTarget t : byTargetList) {
            byTargetMap.put(t.entries.get(0).getBytecodeIndexHandler(), t);
        }
//
//        for (ByTarget t : byTargetList) {
//            List<ExceptionTableEntry> e = t.entries;
//            for (int x=1;x<e.size();++x) {
//                ExceptionTableEntry e1 = e.get(x-1);
//                ExceptionTableEntry e2 = e.get(x);
//                ByTarget alternate = byTargetMap.get(e2.getBytecodeIndexFrom());
//                if (alternate == null) continue;
//                for (ExceptionTableEntry o : alternate.entries) {
//                    if (o == null) continue;
//                    if (o.getBytecodeIndexFrom() == e1.getBytecodeIndexFrom() &&
//                        o.getBytecodeIndexTo() == e1.getBytecodeIndexTo()) {
//                        JavaRefTypeInstance t1 = o.getCatchType(cp);
//                        JavaRefTypeInstance t2 = e1.getCatchType(cp);
//                        boolean cast1 = t1.implicitlyCastsTo(t2);
//                        boolean cast2 = t2.implicitlyCastsTo(t1);
//                        if ((cast1 || cast2)) {
//                            e.set(x, e1.copyWithRange(e1.getBytecodeIndexFrom(), e2.getBytecodeIndexTo()));
//                            e.set(x-1, null);
//                            e.remove(x-1);
//                            x--;
//                        }
//                    }
//                }
//            }
//        }
//
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
        List<ExceptionGroup> rawExceptionsByRange = ListFactory.newList();
        for (ExceptionTableEntry e : rawExceptions) {
            if (prev == null || compareExceptionTablesByStart.compare(e, prev) != 0) {
                currentGroup = new ExceptionGroup(e.getBytecodeIndexFrom(), blockIdentifierFactory.getNextBlockIdentifier(BlockType.TRYBLOCK), cp);
                rawExceptionsByRange.add(currentGroup);
                prev = e;
            }
            currentGroup.add(e);
        }


        exceptionsByRange.addAll(rawExceptionsByRange);
    }

    public List<ExceptionGroup> getExceptionsGroups() {
        return exceptionsByRange;
    }

    /*
     * Remove try statements which simply jump to monitorexit+ , throw statements.
     */
    public void removeSynchronisedHandlers(final Map<Integer, Integer> lutByOffset,
                                           final Map<Integer, Integer> lutByIdx,
                                           List<Op01WithProcessedDataAndByteJumps> instrs) {
        Iterator<ExceptionGroup> groupIterator = exceptionsByRange.iterator();
        ExceptionGroup prev = null;
        while (groupIterator.hasNext()) {
            ExceptionGroup group = groupIterator.next();
            boolean prevSame = false;
            if (prev != null) {
                List<ExceptionGroup.Entry> groupEntries = group.getEntries();
                List<ExceptionGroup.Entry> prevEntries = prev.getEntries();
                if (groupEntries.equals(prevEntries)) {
                    prevSame = true;
                }
            }
            group.removeSynchronisedHandlers(lutByOffset, lutByIdx, instrs);
            if (group.getEntries().isEmpty()) {
                groupIterator.remove();
            } else {
                prev = group;
            }
        }
    }

    /*
     * Remove any exception handlers which can't possibly do anything useful.
     *
     * i.e.
     *
     * try {
     *   x
     * } catch (e) {
     *   throw e;
     * }
     *
     * We have to be very careful here, as it's not valid to do this if the exception handler
     * is one of multiple exception handlers for the same block - i.e.
     *
     * try {
     *  x
     * } catch (e) {
     *  throw e
     * } catch (f) {
     *  // do stuff
     * }
     *
     * for any given catch-rethrow block, we can remove it IF the range covered by its try handler is not covered
     * by any other try handler.
     *
     * We should then re-cover the try block with the coverage which is applied to the exception handler (if any).
     *
     */
    public void aggressivePruning(final Map<Integer, Integer> lutByOffset,
                                  final Map<Integer, Integer> lutByIdx,
                                  List<Op01WithProcessedDataAndByteJumps> instrs) {
        Iterator<ExceptionGroup> groupIterator = exceptionsByRange.iterator();
        while (groupIterator.hasNext()) {
            ExceptionGroup group = groupIterator.next();
            List<ExceptionGroup.Entry> entries = group.getEntries();
            if (entries.size() != 1) continue;
            ExceptionGroup.Entry entry = entries.get(0);
            int handler = entry.getBytecodeIndexHandler();
            Integer index = lutByOffset.get(handler);
            if (index == null) continue;
            Op01WithProcessedDataAndByteJumps handlerStartInstr = instrs.get(index);
            if (handlerStartInstr.getJVMInstr() == JVMInstr.ATHROW) {
                groupIterator.remove();
                continue;
            }
        }
    }
}
