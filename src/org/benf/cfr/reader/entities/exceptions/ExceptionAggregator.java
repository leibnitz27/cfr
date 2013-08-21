package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.*;
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
                               final ConstantPool cp) {

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
}
