package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.Cleaner;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.Misc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.TypeFilter;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheckSimple;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.*;

public class Op03Blocks {

    private static <T> T getSingle(Set<T> in) {
        return in.iterator().next();
    }

    private static List<Block3> doTopSort(List<Block3> in) {
        /*
         * Topological sort, preferring earlier.
         *
         * We can't do a naive 'emit with 0 parents because of loops.
         */
        LinkedHashSet<Block3> allBlocks = new LinkedHashSet<Block3>();
        allBlocks.addAll(in);


        /* in a simple top sort, you take a node with 0 parents, emit it, remove it as parent from all
         * its children, rinse and repeat.  We can't do that immediately, because we have cycles.
         *
         * v1 - try simple top sort, but when we have no candidates, emit next candidate with only existing
         * later parents.
         */
        Set<Block3> ready = new TreeSet<Block3>();
        ready.add(in.get(0));

        List<Block3> output = ListFactory.newList(in.size());

        Block3 last = null;
        while (!allBlocks.isEmpty()) {
            Block3 next = null;
            if (!ready.isEmpty()) {
                /*
                 * Take first known ready
                 */
                next = getSingle(ready);
                ready.remove(next);
            } else {
                /*
                 * Take first of others.
                 */
                next = getSingle(allBlocks);
            }
            last = next;
            // Remove from allblocks so we don't process again.
            allBlocks.remove(next);
            output.add(next);
            Set<BlockIdentifier> fromSet = next.getEnd().getBlockIdentifiers();
            Set<Block3> notReadyInThis = null;
            for (Block3 child : next.targets) {
                child.sources.remove(next);
                if (child.sources.isEmpty()) {
                    if (allBlocks.contains(child)) {
                        ready.add(child);
                    }
                } else {
                    if (child.getStart().getBlockIdentifiers().equals(fromSet)) {
                        if (notReadyInThis == null) notReadyInThis = new TreeSet<Block3>();
                        notReadyInThis.add(child);
                    }
                }
            }
            /* If we're /currently/ in a block set, and the next ready element is
             * NOT in the same blockset, AND WE HAVE CHILDREN IN THE BLOCKSET
             * mark our earliest child as ready, even if it isn't necessarily so.
             */
            if (!ready.isEmpty()) {
                Block3 probnext = getSingle(ready);

                Set<BlockIdentifier> probNextBlocks = probnext.getStart().getBlockIdentifiers();
                /*
                 * If probNextBlocks is a SUBSET of fromset, then try to stay in fromset.
                 */
                if (fromSet.containsAll(probNextBlocks) && !probNextBlocks.equals(fromSet)) {
                    if (notReadyInThis != null && !notReadyInThis.isEmpty()) {
                        Block3 forceChild = getSingle(notReadyInThis);
                        if (allBlocks.contains(forceChild)) {
                            // UNLESS.... (oh god this is awful, dex2jar will have backjumps into monitor
                            // releases..... ) the child we're about to release has a back jump into it
                            // which is NOT from the block (!!!).
                            // [ eg com/db4o/cs/internal/ClientTransactionPool.class ]
                            boolean canForce = true;
                            for (Block3 forceSource : forceChild.sources) {
                                if (forceChild.startIndex.isBackJumpFrom(forceSource.startIndex)) {
                                    if (!forceSource.getStart().getBlockIdentifiers().containsAll(fromSet)) {
                                        canForce = false;
                                        break;
                                    }
                                }
                            }
                            if (canForce) {
                                forceChild.sources.clear();
                                ready.add(forceChild);
                            }
                        }
                    }
                }
            }
        }
        return output;
    }

    private static void apply0TargetBlockHeuristic(List<Block3> blocks) {
        /*
         * If a block has no targets, make sure it appears AFTER its last source
         * in the sorted blocks, [todo : unless that would move it out of a known blockidentifier set.]
         */
        for (int idx = blocks.size() - 1; idx >= 0; idx--) {
            Block3 block = blocks.get(idx);
            if (block.targets.isEmpty()) {
                boolean move = false;
                Block3 lastSource = block;
                for (Block3 source : block.sources) {
                    if (lastSource.compareTo(source) < 0) {
                        move = true;
                        lastSource = source;
                    }
                }
                if (move) {
                    /* If one of the source blocks was a fall through target, we need to change that to
                     * have a goto.
                     *
                     */
                    if (idx > 0) {
                        Block3 fallThrough = blocks.get(idx-1);
                        if (block.sources.contains(fallThrough) && lastSource != fallThrough) {
                            Op03SimpleStatement lastop = fallThrough.getEnd();
                            if (lastop.getStatement().fallsToNext()) {
                                continue;
                            }
                        }
                    }
                    block.startIndex = lastSource.startIndex.justAfter();
                    blocks.add(blocks.indexOf(lastSource) + 1, block);
                    blocks.remove(idx);
                }
            }
        }
    }

//    private static void applyLastBlockHeuristic(List<Block3> blocks) {
//        int last = blocks.size()-1;
//        if (last < 1) return;
//        Block3 lastBlock = blocks.get(last);
//        if (lastBlock.targets.isEmpty()) {
//            lastBlock.addSource(blocks.get(last-1));
//        }
//    }


    private static void removeAliases(Set<BlockIdentifier> in, Map<BlockIdentifier, BlockIdentifier> aliases) {
        Set<BlockIdentifier> toRemove = SetFactory.newSet();
        for (BlockIdentifier i : in) {
            BlockIdentifier alias = aliases.get(i);
            if (alias != null) {
                if (in.contains(alias)) {
                    toRemove.add(i);
                    toRemove.add(alias);
                }
            }
        }
        in.removeAll(toRemove);
    }

    private static Map<BlockIdentifier, BlockIdentifier> getTryBlockAliases(List<Op03SimpleStatement> statements) {
        Map<BlockIdentifier, BlockIdentifier> tryBlockAliases = MapFactory.newMap();
        List<Op03SimpleStatement> catchStatements = Functional.filter(statements, new TypeFilter<CatchStatement>(CatchStatement.class));
        catchlist:
        for (Op03SimpleStatement catchStatementCtr : catchStatements) {
            CatchStatement catchStatement = (CatchStatement) catchStatementCtr.getStatement();
            List<ExceptionGroup.Entry> caught = catchStatement.getExceptions();
            /*
             * If all of the types caught are the same, we temporarily alias the try blocks.
             */
            if (caught.isEmpty()) continue;
            ExceptionGroup.Entry first = caught.get(0);
            JavaRefTypeInstance catchType = first.getCatchType();
            BlockIdentifier tryBlockMain = first.getTryBlockIdentifier();

            List<BlockIdentifier> possibleAliases = ListFactory.newList();
            for (int x = 1, len = caught.size(); x < len; ++x) {
                ExceptionGroup.Entry entry = caught.get(x);
                if (!entry.getCatchType().equals(catchType)) continue catchlist;
                BlockIdentifier tryBlockIdent = entry.getTryBlockIdentifier();
                possibleAliases.add(tryBlockIdent);
            }

            for (Op03SimpleStatement source : catchStatementCtr.getSources()) {
                if (source.getBlockIdentifiers().contains(tryBlockMain)) continue catchlist;
            }

            for (BlockIdentifier alias : possibleAliases) {
                BlockIdentifier last = tryBlockAliases.put(alias, tryBlockMain);
                if (last != null && last != tryBlockMain) {
                    // We're going to have problems....
                    int a = 1;
                }
            }
        }
        return tryBlockAliases;
    }

    /*
     * Heuristic - we don't want to reorder entries which leave known blocks - SO... if a source
     * is in a different blockset, we have to wait until the previous block is emitted.
     */
    private static void applyKnownBlocksHeuristic(final Method method, List<Block3> blocks, Map<BlockIdentifier, BlockIdentifier> tryBlockAliases) {

        /* Find last statement in each block */
        Map<BlockIdentifier, Block3> lastByBlock = MapFactory.newMap();
        for (Block3 block : blocks) {
            for (BlockIdentifier blockIdentifier : block.getStart().getBlockIdentifiers()) {
                lastByBlock.put(blockIdentifier, block);
            }
        }

        Block3 linPrev = null;
        for (Block3 block : blocks) {
            Op03SimpleStatement start = block.getStart();
            Set<BlockIdentifier> startIdents = start.getBlockIdentifiers();
            boolean needLinPrev = false;
            prevtest:
            if (block.sources.contains(linPrev)) {
                Block3 source = linPrev;
                Set<BlockIdentifier> endIdents = source.getEnd().getBlockIdentifiers();
                if (!endIdents.equals(startIdents)) {
                    // If the only difference is case statements, then we allow, unless it's a direct
                    // predecessor
//                    if (source == linPrev) {
//                        needLinPrev = true;
//                        break prevtest;
//                    } else {
                    {
                        Set<BlockIdentifier> diffs = SetUtil.difference(endIdents, startIdents);
                        // Remove aliases from consideration.

                        // If we've just jumped INTO a try block, consider us as being in that too.
                        BlockIdentifier newTryBlock = null;
                        if (block.getStart().getStatement() instanceof TryStatement) {
                            newTryBlock = ((TryStatement) block.getStart().getStatement()).getBlockIdentifier();
                            if (!diffs.add(newTryBlock)) newTryBlock = null;
                        }

                        removeAliases(diffs, tryBlockAliases);

                        for (BlockIdentifier blk : diffs) {
                            if (blk.getBlockType() == BlockType.CASE ||
                                blk.getBlockType() == BlockType.SWITCH) continue;
                            if (blk == newTryBlock) continue;
                            needLinPrev = true;
                            break prevtest;
                        }
                    }
                }
            }
            if (needLinPrev) {
                block.addSource(linPrev);
            } else {
                Op03SimpleStatement blockStart = block.getStart();
                Statement statement = blockStart.getStatement();
                // Should do this for catch as well...
                if (statement instanceof FinallyStatement) {
                    for (Block3 source : ListFactory.newList(block.sources)) {
                        Statement last = source.getEnd().getStatement();
                        if (last instanceof TryStatement) {
                            TryStatement tryStatement = (TryStatement) last;
                            Block3 lastDep = lastByBlock.get(tryStatement.getBlockIdentifier());
                            if (lastDep != null) {
                                block.addSource(lastDep);
                            }
                        }
                    }
                }
            }
            linPrev = block;
        }
    }

    private static List<Block3> buildBasicBlocks(final Method method, final List<Op03SimpleStatement> statements) {
        /*
         *
         */
        final List<Block3> blocks = ListFactory.newList();
        final Map<Op03SimpleStatement, Block3> starts = MapFactory.newMap();
        final Map<Op03SimpleStatement, Block3> ends = MapFactory.newMap();

        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(statements.get(0), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                Block3 block = new Block3(arg1);
                starts.put(arg1, block);
                while (arg1.getTargets().size() == 1) {
                    Op03SimpleStatement next = arg1.getTargets().get(0);
                    if (next.getSources().size() == 1 && arg1.getBlockIdentifiers().equals(next.getBlockIdentifiers())) {
                        arg1 = next;
                        block.append(arg1);
                    } else {
                        break;
                    }
                }
                blocks.add(block);
                ends.put(arg1, block);
                arg2.enqueue(arg1.getTargets());
            }
        });
        gv.process();
        Collections.sort(blocks);

        for (Block3 block : blocks) {
            Op03SimpleStatement start = block.getStart();
            List<Op03SimpleStatement> prevList = start.getSources();
            List<Block3> prevBlocks = ListFactory.newList(prevList.size());
            for (Op03SimpleStatement prev : prevList) {
                Block3 prevEnd = ends.get(prev);
                if (prevEnd == null) {
                    throw new IllegalStateException("Topological sort failed, explicitly disable");
                }
                prevBlocks.add(prevEnd);
            }

            Op03SimpleStatement end = block.getEnd();
            List<Op03SimpleStatement> afterList = end.getTargets();
            List<Block3> postBlocks = ListFactory.newList(afterList.size());
            for (Op03SimpleStatement after : afterList) {
                postBlocks.add(starts.get(after));
            }

            block.addSources(prevBlocks);
            block.addTargets(postBlocks);

            if (end.getStatement() instanceof TryStatement) {
                List<Block3> depends = ListFactory.newList();
                for (Block3 tgt : postBlocks) {
                    tgt.addSources(depends);
                    for (Block3 depend : depends) {
                        depend.addTarget(tgt);
                    }
                    depends.add(tgt);
                }
            }
        }
        return blocks;
    }

    /*
     * Very cheap version of loop detection, to see if we've moved something out incorrectly.
     *
     * If we've got what looks like a loop (backjump where first instruction is a if)
     * but the if jumps into the middle of the loop, make the middle instruction (ie. target of the if)
     * also dependent on the final (backjumping) block.
     *
     * Particularly bad:
     *
     * com/google/android/gms/internal/c\$j.class k
     * com/mobtaxi/android/driver/app/LoginActivity\$UserLoginTask.class
     * com/db4o/io/b.class
     * com/strobel/decompiler/languages/java/ast/NameVariables.class
     * org/acra/SendWorker.class
     *
     * If these are working, we're probably quite happy!
     */
    private static boolean detectMoves(List<Block3> blocks, Options options) {
        Map<Op03SimpleStatement, Block3> opLocations = MapFactory.newIdentityMap();
        Map<Block3, Integer> idxLut = MapFactory.newIdentityMap();
        for (int i = 0, len = blocks.size(); i < len; ++i) {
            Block3 blk = blocks.get(i);
            idxLut.put(blk, i);
            for (Op03SimpleStatement stm : blk.getContent()) {
                opLocations.put(stm, blk);
            }
        }

        BlockIdentifierFactory blockIdentifierFactory = new BlockIdentifierFactory();
        List<Set<BlockIdentifier>> blockMembers = ListFactory.newList();
        for (int i = 0, len = blocks.size(); i < len; ++i) {
            blockMembers.add(SetFactory.<BlockIdentifier>newSet());
        }
        Map<BlockIdentifier, Block3> firstByBlock = MapFactory.newMap();
        Map<BlockIdentifier, Block3> lastByBlock = MapFactory.newMap();
        for (int i = 0, len = blocks.size(); i < len; ++i) {
            Block3 block = blocks.get(i);
            Block3 lastBackJump = block.getLastUnconditionalBackjumpToHere(idxLut);
            if (lastBackJump != null) {
                BlockIdentifier bid = blockIdentifierFactory.getNextBlockIdentifier(BlockType.DOLOOP);
                for (int x = i+1, last = idxLut.get(lastBackJump); x <= last; ++x) {
                    blockMembers.get(x).add(bid);
                }
                firstByBlock.put(bid, block);
                lastByBlock.put(bid, lastBackJump);
            }
        }

        /*
         * Now, identify blocks which are the target of a forward jump into the middle of a DOLoop,
         * from before that loop started.
         * Make these blocks depend (spuriously) on the lastByBlock for that DOLoop.
         */
        boolean effect = false;
        if (options.getOption(OptionsImpl.FORCE_TOPSORT_EXTRA) == Troolean.TRUE) {
            outer2:
            for (int i = 0, len = blocks.size(); i < len; ++i) {
                Block3 block = blocks.get(i);
                if (!block.targets.isEmpty()) continue;
                Set<BlockIdentifier> inThese = blockMembers.get(i);
                if (inThese.isEmpty()) continue;
                for (Block3 source : block.originalSources) {
                    int j = idxLut.get(source);
                    if (j < i) {
                        Set<BlockIdentifier> sourceInThese = blockMembers.get(j);
                        if (!sourceInThese.containsAll(inThese)) {
    //                        if (block.originalSources.contains(blocks.get(i-1))) {
    //                            if (blockMembers.get(i-1).containsAll(inThese)) continue;
    //                        }
                            // Unless block is the BEGINNING of the missing one, make block depend on the END
                            // of the loops which have been jumped into.
                            Set<BlockIdentifier> tmp = SetFactory.newSet(inThese);
                            tmp.removeAll(sourceInThese);
                            List<Block3> newSources = ListFactory.newList();
                            for (BlockIdentifier jumpedInto : tmp) {
                                if (firstByBlock.get(jumpedInto) != block) {
                                    newSources.add(lastByBlock.get(jumpedInto));
                                }
                            }
                            if (!newSources.isEmpty()) {
                                block.addSources(newSources);
                                effect = true;
                                continue outer2;
                            }
                        }
                    }
                }
            }

        } else {
            outer:
            for (Map.Entry<BlockIdentifier, Block3> entry : firstByBlock.entrySet()) {
                BlockIdentifier ident = entry.getKey();
                Block3 block = entry.getValue();
                Op03SimpleStatement first = block.getStart();
                Statement statement = first.getStatement();
                if (statement instanceof IfStatement) {
                    List<Op03SimpleStatement> tgts = first.getTargets();
                    if (tgts.size() != 2) continue;

                    Op03SimpleStatement tgt = tgts.get(1);
                    Block3 blktgt = opLocations.get(tgt);
                    if (!blockMembers.get(idxLut.get(blktgt)).contains(ident)) continue;
                    if (lastByBlock.get(ident) == blktgt) continue;

                    Set<Block3> origSources = SetFactory.newSet(blktgt.originalSources);
                    origSources.remove(block);
                    for (Block3 src : origSources) {
                        if ((blockMembers.get(idxLut.get(src)).contains(ident) && src.startIndex.isBackJumpFrom(blktgt.startIndex)) ||
                                src.startIndex.isBackJumpFrom(block.startIndex)) {
                            // We give blktgt an additional source of the END of the loop.
                            blktgt.addSource(lastByBlock.get(ident));
                            effect = true;
                            continue outer;
                        }
                    }
                }
            }
        }

        if (effect) {
            for (Block3 block : blocks) {
                block.copySources();
            }
        }
        return effect;
    }

    private static void stripTryBlockAliases(List<Op03SimpleStatement> out, Map<BlockIdentifier, BlockIdentifier> tryBlockAliases) {

        List<Op03SimpleStatement> remove = ListFactory.newList();

        for (int x = 1, len = out.size(); x < len; ++x) {
            Op03SimpleStatement s = out.get(x);
            if (s.getStatement().getClass() != TryStatement.class) continue;
            /*
             * Has this been moved directly after a member of an alias?  If so, we can remove the statement, and relabel
             * anything that belonged to it.
             */
            TryStatement tryStatement = (TryStatement) s.getStatement();
            BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
            Op03SimpleStatement prev = out.get(x - 1);
            BlockIdentifier alias = tryBlockAliases.get(tryBlock);
            if (alias == null) continue;

            if (prev.getBlockIdentifiers().contains(alias)) remove.add(s);

        }
        if (remove.isEmpty()) return;
        for (Op03SimpleStatement removeThis : remove) {
            TryStatement removeTry = (TryStatement) removeThis.getStatement();
            BlockIdentifier blockIdentifier = removeTry.getBlockIdentifier();
            BlockIdentifier alias = tryBlockAliases.get(blockIdentifier);

            /*
             * Unlink, and replace all occurences of blockIdentifier with alias.
             */
            List<Op03SimpleStatement> targets = removeThis.getTargets();
            Op03SimpleStatement naturalTarget = targets.get(0);
            for (Op03SimpleStatement target : targets) {
                target.removeSource(removeThis);
            }
            for (Op03SimpleStatement source : removeThis.getSources()) {
                source.replaceTarget(removeThis, naturalTarget);
                naturalTarget.addSource(source);
            }
            removeThis.clear();
            for (Op03SimpleStatement statement : out) {
                statement.replaceBlockIfIn(blockIdentifier, alias);
            }
        }
    }

    public static List<Op03SimpleStatement> combineTryBlocks(final Method method, final List<Op03SimpleStatement> statements) {
        Map<BlockIdentifier, BlockIdentifier> tryBlockAliases = getTryBlockAliases(statements);
        stripTryBlockAliases(statements, tryBlockAliases);
        return Cleaner.removeUnreachableCode(statements, true);
    }

    private static boolean canCombineBlockSets(Block3 from, Block3 to) {

        Set<BlockIdentifier> fromBlocks = from.getStart().getBlockIdentifiers();
        Set<BlockIdentifier> toBlocks = to.getStart().getBlockIdentifiers();

        if (fromBlocks.equals(toBlocks)) return true;
        // If we've moved from a case label to a case block those can be combined.

        fromBlocks = from.getEnd().getBlockIdentifiers();
        if (fromBlocks.equals(toBlocks)) return true;

        if (fromBlocks.size() == toBlocks.size()-1) {
            Statement stm = from.getEnd().getStatement();
            if (stm instanceof CaseStatement) {
                BlockIdentifier caseBlock = ((CaseStatement) stm).getCaseBlock();
                List<BlockIdentifier> diff = SetUtil.differenceAtakeBtoList(toBlocks, fromBlocks);
                if (diff.size() == 1 && diff.get(0) == caseBlock) return true;
            }
        }
        return false;
    }


    private static List<Block3> sanitiseBlocks(final Method method, List<Block3> blocks) {
        for (Block3 block : blocks) {
            block.sources.remove(block);
            block.targets.remove(block);
        }
        return blocks;
    }

    /*
     * Look for blocks with multiple targets, where one of the targets has 0 targets itself (AND ONLY ONE SOURCE).
     * and is NOT a neighbour, and the last 0p3 in the source block is a conditional.
     *
     * if that's the case, we can reorder the sense of the final conditional to jump OVER the 0 target
     * block into wherever it was going, and move the 0 target block immediately after its source.
     *
     * In order to satisfy other tests, cause the conditional to jump to the END of the combined block, and THAT
     * can jump to the original target.
     *
     * We have to do this BEFORE we combine neighbouring blocks, otherwise we may unsatisfy the last-op-conditional
     * predicate.
     *
     * Note that this bears quite a lot of similarity to moveSingleOutOrderBlocks, however that needs
     * to happen AFTER neighbouring blocks have been combined.
     *
     * THIS COULD PROBABLY BE REFACTORED TO REDUCE DUPLICATE CODE.
     */
    private static List<Block3> invertJoinZeroTargetJumps(final Method method, List<Block3> blocks) {
        final Map<Op03SimpleStatement, Block3> seenPrevBlock = MapFactory.newMap();
        boolean effect = false;
        testOne : for (int x = 0, len = blocks.size(); x<len;++x) {
            Block3 block = blocks.get(x);

            // We keep track of the start of a block to the linearly previous, to discount the case
            // where there's a conditional with two block targets, BOTH of which are 0 target.
            // re-ordering in that circumstance probably buys us nothing, and moves us further away
            // from the original code.

            if (block.sources.size() == 1 && block.targets.size() == 0) {
                if (x > 0) seenPrevBlock.put(block.getStart(), blocks.get(x-1));

                Block3 source = getSingle(block.sources);
                /*
                 * Verify that source ends with a conditional that positive jumps to this target.
                 */
                Op03SimpleStatement sourceEnd = source.getEnd();
                Statement statement = sourceEnd.getStatement();
                if (statement.getClass() != IfStatement.class) continue;
                IfStatement ifStatement = (IfStatement)statement;
                // But does it TAKEN jump to the dangling block?
                List<Op03SimpleStatement> targets = sourceEnd.getTargets();
                if (targets.size() != 2) continue;
                Op03SimpleStatement taken = targets.get(1);
                if (taken != block.getStart()) continue;
                // ARE THE Block Idents THE SAME?
                if (!sourceEnd.getBlockIdentifiers().equals(taken.getBlockIdentifiers())) continue;
                Op03SimpleStatement notTaken = targets.get(0);

                // If the OTHER target also was a block3 with no targets, we're not going to improve the situation.
                Block3 notTakenPrevBlock = seenPrevBlock.get(notTaken);
                if (notTakenPrevBlock == source) continue;

                // ok - flip the sense of the test, make it fall through to the jump site, and jump to the fall through
                ifStatement.setCondition(ifStatement.getCondition().getNegated());

                source.getContent().addAll(block.getContent());
                block.getContent().clear();
                block.sources.clear();
                source.targets.remove(block);

                // index doesn't really matter, we'll re-index at combination.
                Op03SimpleStatement newGoto = new Op03SimpleStatement(sourceEnd.getBlockIdentifiers(), new GotoStatement(), sourceEnd.getIndex().justAfter());
                source.getContent().add(newGoto);

                // What was 'taken' becomes the fallthrough, and newGoto becomes the explicit taken target.
                sourceEnd.replaceTarget(taken, newGoto);
                sourceEnd.replaceTarget(notTaken, taken);

                notTaken.replaceSource(sourceEnd, newGoto);
                newGoto.addSource(sourceEnd);
                newGoto.addTarget(notTaken);

                blocks.set(x, null);
                effect = true;
            }
        }
        if (effect) {
            blocks = Functional.filter(blocks, new Functional.NotNull<Block3>());
        }
        return blocks;
    }

    private static List<Block3> combineNeighbouringBlocks(final Method method, List<Block3> blocks) {
        boolean reloop = false;
        do {
            blocks = combineNeighbouringBlocksPass1(method, blocks);
            reloop = moveSingleOutOrderBlocks(method, blocks);
        } while (reloop);
        // Now try to see if we can move single blocks into place.
        return blocks;
    }

    /*
     * We're looking for a very specific feature here - a case statement which jumps BACK immediately, to something which has NO
     * OTHER source.  This won't get very many exemplars.
     */
    private static List<Block3> combineSingleCaseBackBlock(final Method method, List<Block3> blocks) {
        IdentityHashMap<Block3, Integer> idx = new IdentityHashMap<Block3, Integer>();

        boolean effect = false;
        for (int x = 0, len = blocks.size(); x<len;++x) {
            Block3 block = blocks.get(x);
            idx.put(block, x);
            if (block.targets.size() == 1 && block.content.size() == 2) {
                Block3 target = getSingle(block.targets);
                Integer tgtIdx = idx.get(target);
                if (tgtIdx == null) continue;
                if (target.sources.size() != 1) continue;
                List<Op03SimpleStatement> content = block.content;
                if (content.get(0).getStatement().getClass() != CaseStatement.class) continue;
                if (content.get(1).getStatement().getClass() != GotoStatement.class) continue;
                Set<BlockIdentifier> containedIn = content.get(1).getBlockIdentifiers();

                content = target.getContent();
                for (Op03SimpleStatement statement : content) {
                    statement.getBlockIdentifiers().addAll(containedIn);
                }
                block.content.addAll(content);
                target.sources.remove(block);
                block.targets.remove(target);
                for (Block3 tgt2 : target.targets) {
                    tgt2.sources.remove(target);
                    tgt2.sources.add(block);
                    block.targets.add(tgt2);
                    tgt2.resetSources();
                }
                target.targets.clear();
                blocks.set(tgtIdx, null);
                effect = true;
            }
        }
        if (effect) {
            blocks = Functional.filter(blocks, new Functional.NotNull<Block3>());
        }
        return blocks;
    }

    /*
     * Look for a block which has a single source and target, where that source and target are elsewhere, but next to each other.
     * If the source ends with a fall through to target, source needs to be augmented with a jump to target.
     *
     * a (maybe fall to c).
     * c
     *
     * ..
     * ..
     *
     * b (from a, to c).
     */
    static boolean moveSingleOutOrderBlocks(final Method method, final List<Block3> blocks) {
        IdentityHashMap<Block3, Integer> idx = new IdentityHashMap<Block3, Integer>();
        for (int x = 0, len = blocks.size(); x<len;++x) {
            idx.put(blocks.get(x), x);
        }

        boolean effect = false;
        testOne : for (int x = 0, len = blocks.size(); x<len;++x) {
            Block3 block = blocks.get(x);
            if (block.sources.size() == 1 && block.targets.size() == 1) {
                Block3 source = getSingle(block.sources);
                Block3 target = getSingle(block.targets);
                int idxsrc = idx.get(source);
                int idxtgt = idx.get(target);
                // If it slots in directly between source and target, use it.
                if (idxsrc == idxtgt-1 && idxtgt < x) {

                    // The jump from source to target has to be the LAST branch in source.
                    List<Op03SimpleStatement> statements = source.getContent();
                    // We can iterate in order, as we're specifically looking for a non fall through.
                    Op03SimpleStatement prev = null;
                    for (int y=statements.size()-1;y>=0;--y) {
                        Op03SimpleStatement stm = statements.get(y);
                        Statement statement = stm.getStatement();
                        if (!statement.fallsToNext()) {
                            // If this statement doesn't have two targets of PREV and the start of BLOCK, can't do it.
                            List<Op03SimpleStatement> stmTargets = stm.getTargets();
                            if (stmTargets.size() != 2 ||
                                stmTargets.get(0) != prev ||
                                stmTargets.get(1) != block.getStart())
                                continue testOne;
                            break;
                        }
                        prev = stm;
                    }

                    if (!canCombineBlockSets(source, block)) {
                        // Extract this block check into a function?
                        Set<BlockIdentifier> srcBlocks = source.getEnd().getBlockIdentifiers();
                        Set<BlockIdentifier> midBlocks = block.getStart().getBlockIdentifiers();
                        if (srcBlocks.size() != midBlocks.size()+1) continue;

                        List<BlockIdentifier> diff = SetUtil.differenceAtakeBtoList(srcBlocks, midBlocks);
                        if (diff.size() != 1) continue;
                        BlockIdentifier blk = diff.get(0);
                        if (blk.getBlockType() != BlockType.TRYBLOCK) continue;
                        // Ok, is it safe to move this content into the try block?
                        for (Op03SimpleStatement op : block.getContent()) {
                            if (op.getStatement().canThrow(ExceptionCheckSimple.INSTANCE)) continue testOne;
                        }
                        block.getStart().markBlock(blk);
                    }


                    // It doesn't matter that we are now invalidating the idx map, it will be
                    // correct relative to local content.
                    blocks.remove(x);
                    int curridx = blocks.indexOf(source);
                    blocks.add(curridx+1, block);
                    block.startIndex = source.startIndex.justAfter();
                    patch(source, block);

                    effect = true;
                }
            }
        }
        return effect;
    }

    private static List<Block3> combineNeighbouringBlocksPass1(final Method method, final List<Block3> blocks) {
        Block3 curr = blocks.get(0);
        int curridx = 0;

        for (int i=1, len=blocks.size(); i<len; ++i) {
            Block3 next = blocks.get(i);
            if (next == null) continue;
            // This pass is too aggressive - this means we will roll both sides of a conditional together, and
            // won't be able to reorder them...
            if (next.sources.size() == 1 && getSingle(next.sources) == curr &&
                next.getStart().getSources().contains(curr.getEnd())) {
                if (canCombineBlockSets(curr,next)) {
                    // If the last of curr is an explicit goto the start of next, cull it.
                    Op03SimpleStatement lastCurr = curr.getEnd();
                    Op03SimpleStatement firstNext = next.getStart();
                    if (lastCurr.getStatement().getClass() == GotoStatement.class && !lastCurr.getTargets().isEmpty() && lastCurr.getTargets().get(0) == firstNext) {
                        lastCurr.nopOut();
                    }

                    // Merge, and repeat.
                    curr.content.addAll(next.content);
                    curr.targets.remove(next);
                    for (Block3 target : next.targets) {
                        target.sources.remove(next);
                        target.sources.add(curr);
                    }
                    next.sources.clear();
                    curr.targets.addAll(next.targets);
                    next.targets.clear();
                    curr.sources.remove(curr);
                    curr.targets.remove(curr);
                    blocks.set(i, null);
                    // Try to rewind current to the last block before it, as we may be able to merge with predencessor
                    // now.
                    for (int j=curridx-1;j>=0;j--) {
                        Block3 tmp = blocks.get(j);
                        if (tmp != null) {
                            curr = tmp;
                            curridx = j;
                            i = j;
                            break;
                        }
                    }
                    continue;
                }
            }
            // couldn't merge, emit and consider.
            curr = next;
            curridx = i;
        }
        for (Block3 block : blocks) {
            if (block != null) block.resetSources();
        }
        return Functional.filter(blocks, new Functional.NotNull<Block3>());
    }

    public static List<Op03SimpleStatement> topologicalSort(final Method method, final List<Op03SimpleStatement> statements, final DecompilerComments comments, final Options options) {

        List<Block3> blocks = buildBasicBlocks(method, statements);

        apply0TargetBlockHeuristic(blocks);

        Map<BlockIdentifier, BlockIdentifier> tryBlockAliases = getTryBlockAliases(statements);
        applyKnownBlocksHeuristic(method, blocks, tryBlockAliases);

        blocks = sanitiseBlocks(method, blocks);
        blocks = invertJoinZeroTargetJumps(method, blocks);
        blocks = combineNeighbouringBlocks(method, blocks);

        // Case statements can vector BACK to blocks which are tricky to move if we rely on a general
        // reachability ordering criteria.
        // see bb/xh.class
        blocks = combineSingleCaseBackBlock(method, blocks);

        blocks = doTopSort(blocks);

        /*
         * A (very) cheap version of location based loop discovery, where we find blocks which appear to have
         * been moved out of order.  I'm SURE this isn't the right way to do it, however it stops us pessimising
         * some very odd cases.  :(
         */
        if (detectMoves(blocks, options)) {
            Collections.sort(blocks);
            blocks = doTopSort(blocks);
        }

        /*
         * Now, we have to patch up with gotos anywhere that we've changed the original ordering.
         * NB. This is the first destructive change.
         */
        for (int i = 0, len = blocks.size(); i < len - 1; ++i) {
            Block3 thisBlock = blocks.get(i);
            Block3 nextBlock = blocks.get(i + 1);
            patch(thisBlock, nextBlock);
        }
        // And a final patch on the last block if it ends in a non goto back jump
        patch(blocks.get(blocks.size() - 1), null);

        /*
         * Now go through, and emit the content, in order.
         */
        List<Op03SimpleStatement> outStatements = ListFactory.newList();
        for (Block3 outBlock : blocks) {
            outStatements.addAll(outBlock.getContent());
        }

        Cleaner.reindexInPlace(outStatements);

        /*
         * Patch up conditionals.
         */
        boolean patched = false;
        for (int x = 0, origLen = outStatements.size() - 1; x < origLen; ++x) {
            Op03SimpleStatement stm = outStatements.get(x);
            if (stm.getStatement().getClass() == IfStatement.class) {
                List<Op03SimpleStatement> targets = stm.getTargets();
                Op03SimpleStatement next = outStatements.get(x + 1);
                if (targets.get(0) == next) {
                    // Nothing.
                } else if (targets.get(1) == next) {
                    IfStatement ifStatement = (IfStatement) stm.getStatement();
                    ifStatement.setCondition(ifStatement.getCondition().getNegated().simplify());
                    Op03SimpleStatement a = targets.get(0);
                    Op03SimpleStatement b = targets.get(1);
                    targets.set(0, b);
                    targets.set(1, a);
                } else {
                    patched = true;
                    // Oh great.  Something's got very interesting. We need to add ANOTHER goto.
                    Op03SimpleStatement extra = new Op03SimpleStatement(stm.getBlockIdentifiers(), new GotoStatement(), stm.getSSAIdentifiers(), stm.getIndex().justAfter());
                    Op03SimpleStatement target0 = targets.get(0);
                    extra.addSource(stm);
                    extra.addTarget(target0);
                    stm.replaceTarget(target0, extra);
                    target0.replaceSource(stm, extra);
                    outStatements.add(extra);
                }
            }
        }

        if (patched) {
            outStatements = Cleaner.sortAndRenumber(outStatements);
        }

        stripTryBlockAliases(outStatements, tryBlockAliases);

        /*
         * Strip illegal try backjumps - note that if this has effect, it may change the semantics of the outputted code
         * so we must warn.
         */
        if (options.getOption(OptionsImpl.ALLOW_CORRECTING)) {
            if (stripBackExceptions(outStatements)) {
                comments.addComment(DecompilerComment.TRY_BACKEDGE_REMOVED);
            }
        }

        return Cleaner.removeUnreachableCode(outStatements, true);
    }

    private static boolean stripBackExceptions(List<Op03SimpleStatement> statements) {
        boolean res = false;
        List<Op03SimpleStatement> tryStatements = Functional.filter(statements, new Op03SimpleStatement.ExactTypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement statement : tryStatements) {
            TryStatement tryStatement = (TryStatement) statement.getStatement();

            if (statement.getTargets().isEmpty()) continue;
            Op03SimpleStatement fallThrough = statement.getTargets().get(0);
            List<Op03SimpleStatement> backTargets = Functional.filter(statement.getTargets(), new Misc.IsForwardJumpTo(statement.getIndex()));
            boolean thisRes = false;
            for (Op03SimpleStatement backTarget : backTargets) {
                Statement backTargetStatement = backTarget.getStatement();
                if (backTargetStatement.getClass() == CatchStatement.class) {
                    CatchStatement catchStatement = (CatchStatement) backTargetStatement;
                    catchStatement.getExceptions().removeAll(tryStatement.getEntries());
                    backTarget.removeSource(statement);
                    statement.removeTarget(backTarget);
                    thisRes = true;
                }
            }
            /*
             * Remove the try statement completely if all
             */
            if (thisRes) {
                res = true;
                List<Op03SimpleStatement> remainingTargets = statement.getTargets();
                if (remainingTargets.size() == 1 && remainingTargets.get(0) == fallThrough) {
                    statement.nopOut();
                }
            }
        }
        return res;
    }

    private static void patch(Block3 a, Block3 b) {
        /*
         * Look at the last statement of a - does it expect to continue on to the next
         * statement, which may now have moved?
         */
        List<Op03SimpleStatement> content = a.content;
        Op03SimpleStatement last = content.get(content.size() - 1);
        Statement statement = last.getStatement();

        if (last.getTargets().isEmpty() || !statement.fallsToNext()) return;

        // The 'fallthrough' target is always the 0th one.
        Op03SimpleStatement fallThroughTarget = last.getTargets().get(0);
        if (b != null && fallThroughTarget == b.getStart()) return;

        /*
         * Ok, we have reordered something in a way that will cause problems.
         * We need to insert an extra goto, and change relations of the Op03 to handle.
         */
        Op03SimpleStatement newGoto = new Op03SimpleStatement(last.getBlockIdentifiers(), new GotoStatement(), last.getIndex().justAfter());
        a.append(newGoto);
        last.replaceTarget(fallThroughTarget, newGoto);
        newGoto.addSource(last);
        newGoto.addTarget(fallThroughTarget);
        fallThroughTarget.replaceSource(last, newGoto);
    }

    private static class Block3 implements Comparable<Block3> {
        InstrIndex startIndex;
        List<Op03SimpleStatement> content = ListFactory.newList();
        Set<Block3> sources = new LinkedHashSet<Block3>();
        // This seems redundant? - verify need.
        Set<Block3> originalSources = new LinkedHashSet<Block3>();
        Set<Block3> targets = new LinkedHashSet<Block3>();


        public Block3(Op03SimpleStatement s) {
            startIndex = s.getIndex();
            content.add(s);
        }

        public void append(Op03SimpleStatement s) {
            content.add(s);
        }

        public Op03SimpleStatement getStart() {
            return content.get(0);
        }

        public Op03SimpleStatement getEnd() {
            return content.get(content.size() - 1);
        }

        public void addSources(List<Block3> sources) {
            for (Block3 source : sources) {
                if (source == null) {
                    throw new IllegalStateException();
                }
            }
            this.sources.addAll(sources);
            this.originalSources.addAll(sources);
        }

        public void addSource(Block3 source) {
            this.sources.add(source);
            this.originalSources.add(source);
        }

        public void setTargets(List<Block3> targets) {
            this.targets.clear();
            this.targets.addAll(targets);
        }

        public void addTargets(List<Block3> targets) {
            for (Block3 source : targets) {
                if (source == null) {
                    throw new IllegalStateException();
                }
            }
            this.targets.addAll(targets);
//            this.originalSources.addAll(sources);
        }

        public void addTarget(Block3 source) {
            this.targets.add(source);
//            this.originalSources.add(source);
        }

        @Override
        public int compareTo(Block3 other) {
            return startIndex.compareTo(other.startIndex);
        }

        @Override
        public String toString() {
            return "(" + content.size() + ")[" + sources.size() + "/" + originalSources.size() + "," + targets.size() + "] " + startIndex + getStart().toString();
        }

        private List<Op03SimpleStatement> getContent() {
            return content;
        }

        public void copySources() {
            sources.clear();
            sources.addAll(originalSources);
        }

        public void resetSources() {
            originalSources.clear();
            originalSources.addAll(sources);
        }

        public Block3 getLastUnconditionalBackjumpToHere(Map<Block3, Integer> idxLut) {
            int thisIdx = idxLut.get(this);
            int best = -1;
            Block3 bestSource = null;
            for (Block3 source : originalSources) {
                if (source.getEnd().getStatement().getClass() == GotoStatement.class) {
                    int idxSource = idxLut.get(source);
                    if (idxSource > best && idxSource > thisIdx) {
                        bestSource = source;
                        best = idxSource;
                    }
                }
            }
            return bestSource;
        }
    }

}
