package org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.util.SetUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Op02RedundantStoreRewriter {
    private static final Op02RedundantStoreRewriter INSTANCE = new Op02RedundantStoreRewriter();

    private Op02RedundantStoreRewriter() {
    }

    /*
     * For every local var store - if we reach another store overwriting without branching / being inside an exception block,
     * this store can be considered to be redundant.
     *
     * Consider
     *
     *       23: iconst_1
      24: istore        5
      26: aload_1
      27: iload         5
      29: baload
      30: istore        5
      32: iload         5
      34: sipush        255
      37: iadd
      38: istore        5
      40: iload         5
      42: bipush        8
      44: ishl
      45: istore        5

      can be rewritten  (introducing the swap is a bit of a stretch....)

       iconst_1
       aload_1
       swap
       baload
       sipush 255
       iadd
       bipush 8
       istore 5
     */
    private void rewriteInstrs(List<Op02WithProcessedDataAndRefs> instrs, int maxLocals) {
//        if (instrs.size() < 1000) return;
        int laststore[] = new int[maxLocals];
        int lastload[] = new int[maxLocals];
        int loadsSinceStore[] = new int[maxLocals];
        int lastCutOff = 0;
        int nopCount = 0;
        Set<BlockIdentifier> currentBlocks = new HashSet<BlockIdentifier>();

        for (int x=0, maxm1=instrs.size()-1;x<maxm1;++x) {
            Op02WithProcessedDataAndRefs instr = instrs.get(x);
            List<Op02WithProcessedDataAndRefs> targets = instr.getTargets();
            List<Op02WithProcessedDataAndRefs> sources = instr.getSources();
            if (sources.size() != 1 || targets.size() != 1 || targets.get(0) != instrs.get(x+1)) {
                lastCutOff = x;
                continue;
            }
            if (!SetUtil.equals(currentBlocks, instr.getContainedInTheseBlocks())) {
                lastCutOff = x;
                currentBlocks = new HashSet<BlockIdentifier>(instr.getContainedInTheseBlocks());
            }
            JVMInstr jvmInstr = instr.getInstr();
            Pair<JavaTypeInstance, Integer> stored = instr.getStorageType();
            /*
             * If it's a store, is there a store after lastcutoff?  If so, we can remove it.
             * If we remove it, all LOADS after lastcutoff have to be replaced.
             */
            if (stored != null) {
                if (jvmInstr == JVMInstr.IINC || jvmInstr == JVMInstr.IINC_WIDE) {
                    // Mutate.  complex. Abandon process path.
                    lastCutOff = x;
                    continue;
                }
                int storeidx = stored.getSecond();
                /*
                 * has there been a SINGLE fetch since last store, that we can make use of?
                 * (one straight after is trivial.  one 2 after is simple (reorder/swap)... ;) )
                 */
                if (laststore[storeidx] > lastCutOff) {
                    int laststoreidx = laststore[storeidx];
                    if (lastload[storeidx] > lastCutOff && loadsSinceStore[storeidx] == 1) {
                        int lastloadidx = lastload[storeidx];
                        if (lastloadidx == laststoreidx+1) {
                            instrs.get(laststoreidx).nop();
                            instrs.get(lastloadidx).nop();
                            nopCount+=2;
                        } else if (lastloadidx == laststoreidx+2) {
                            instrs.get(laststoreidx).nop();
                            instrs.get(lastloadidx).swap();
                            nopCount++;
                        }
                    } else if (loadsSinceStore[storeidx] == 0) {
                        // This is a little funky - if we're NOT inside an exception handler, then
                        // we can discard the PREVIOUS store regardless.
                        // If we ARE inside an exception handler, we can discard THIS store, IFF it's storing
                        // the same register as previously loaded, and THAT register hasn't been altered.
                        // Note - because this is Op02, the only POSSIBLE kind of blocks we could be in are
                        // exception blocks.
                        // Note that we change lastCutoff above when we change handlers.
                        Pair<JavaTypeInstance, Integer> prevFetched = instrs.get(laststoreidx - 1).getRetrieveType();
                        if (prevFetched != null) {
                            if (currentBlocks.isEmpty()) {
                                instrs.get(laststoreidx).nop();
                                instrs.get(laststoreidx - 1).nop();
                                nopCount += 2;
                            } else {
                                Pair<JavaTypeInstance, Integer> thisFetched = instrs.get(x - 1).getRetrieveType();
                                if (thisFetched != null) {
                                    if (thisFetched.getSecond().equals(prevFetched.getSecond())) {
                                        loadsSinceStore[prevFetched.getSecond()]--;
                                        instrs.get(x).nop();
                                        instrs.get(x-1).nop();
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }

                laststore[storeidx] = x;
                loadsSinceStore[storeidx] = 0;
                continue;
            }
            Pair<JavaTypeInstance, Integer> fetched = instr.getRetrieveType();
            if (fetched != null) {
                int fetchidx = fetched.getSecond();
                if (laststore[fetchidx] <= lastCutOff) loadsSinceStore[fetchidx] = 0;
                loadsSinceStore[fetchidx]++;
                lastload[fetchidx] = x;
            }
        }
        if (nopCount > 0) {
            Iterator<Op02WithProcessedDataAndRefs> iterator = instrs.iterator();
            iterator.next();
            while (iterator.hasNext()) {
                Op02WithProcessedDataAndRefs instr = iterator.next();
                if (instr.getInstr() == JVMInstr.NOP) {
                    List<Op02WithProcessedDataAndRefs> targets = instr.getTargets();
                    if (targets.size() != 1) continue;
                    Op02WithProcessedDataAndRefs target = targets.get(0);
                    targets.clear();
                    target.removeSource(instr);
                    List<Op02WithProcessedDataAndRefs> sources = instr.getSources();
                    for (Op02WithProcessedDataAndRefs source : sources) {
                        source.replaceTarget(instr, target);
                        target.addSource(source);
                    }
                    iterator.remove();
                }
            }
            // remove dead code.
        }
    }

    public static void rewrite(List<Op02WithProcessedDataAndRefs> instrs, int maxLocals) {
        INSTANCE.rewriteInstrs(instrs, maxLocals);
    }
}
