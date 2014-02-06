package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/10/2013
 * Time: 06:39
 */
public class Op03Blocks {

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
                next = ready.iterator().next();
                ready.remove(next);
            } else {
                /*
                 * If any of the children of the last block haven't been emitted, emit the first.
                 */
                /*
                if (last != null) {
                    for (Block3 lastChild : last.targets) {
                        if (allBlocks.contains(lastChild)) {
                            next = lastChild;
                            break;
                        }
                    }
                }
                */

                if (next == null) {
                    /*
                     * Take first of others.
                     */
                    next = allBlocks.iterator().next();
                }
            }
            last = next;
            // Remove from allblocks so we don't process again.
            allBlocks.remove(next);
            output.add(next);
            for (Block3 child : next.targets) {
                child.sources.remove(next);
                if (child.sources.isEmpty()) {
                    if (allBlocks.contains(child)) {
                        ready.add(child);
                    }
                }
            }
        }
        return output;
    }

    private static void apply0TargetBlockHeuristic(List<Block3> blocks) {
        /*
         * If a block has no targets, but multiple sources, make sure it appears AFTER its last source
         * in the sorted blocks.
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
        List<Op03SimpleStatement> catchStatements = Functional.filter(statements, new Op03SimpleStatement.TypeFilter<CatchStatement>(CatchStatement.class));
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
    private static void applyKnownBlocksHeuristic(List<Block3> blocks, Map<BlockIdentifier, BlockIdentifier> tryBlockAliases) {


        // FIXME : TODO : INEFFICIENT.  Leaving it like this because I may need to revert.
        Block3 linPrev = null;
        for (Block3 block : blocks) {
            Op03SimpleStatement start = block.getStart();
            Set<BlockIdentifier> startIdents = start.getBlockIdentifiers();
            boolean needLinPrev = false;
            prevtest:
            for (Block3 source : block.sources) {
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
            }
            linPrev = block;
        }
    }

    private static List<Block3> buildBasicBlocks(final List<Op03SimpleStatement> statements) {
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
            block.setTargets(postBlocks);

            if (end.getStatement() instanceof TryStatement) {
                List<Block3> depends = ListFactory.newList();
                for (Block3 tgt : postBlocks) {
                    tgt.addSources(depends);
                    depends.add(tgt);
                }
            }
        }
        return blocks;
    }

    /*
     * Very cheap version of loop detection, to see if we've moved something out incorrectly.
     */
    private static boolean detectMoves(List<Block3> blocks) {
        Map<Block3, Integer> idxLut = MapFactory.newIdentityMap();
        for (int i = 0, len = blocks.size(); i < len; ++i) {
            idxLut.put(blocks.get(i), i);
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
                for (int x = i + 1, last = idxLut.get(lastBackJump); x <= last; ++x) {
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
        outer:
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
                            continue outer;
                        }
                    }
                }
            }
        }
        if (!effect) return false;

        for (Block3 block : blocks) {
            block.copySources();
        }
        return true;
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
        return Op03SimpleStatement.removeUnreachableCode(statements, true);
    }

    public static List<Op03SimpleStatement> topologicalSort(final Method method, final List<Op03SimpleStatement> statements, final DecompilerComments comments, final Options options) {

        List<Block3> blocks = buildBasicBlocks(statements);

        apply0TargetBlockHeuristic(blocks);

        Map<BlockIdentifier, BlockIdentifier> tryBlockAliases = getTryBlockAliases(statements);
        applyKnownBlocksHeuristic(blocks, tryBlockAliases);

        blocks = doTopSort(blocks);

        /*
         * A (very) cheap version of location based loop discovery, where we find blocks which appear to have
         * been moved out of order.  I'm SURE this isn't the right way to do it, however it stops us pessimising
         * some very odd cases.  :(
         */
        if (detectMoves(blocks)) {
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

        int newIndex = 0;
        for (Op03SimpleStatement statement : outStatements) {
            statement.setIndex(new InstrIndex(newIndex++));
        }

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
            outStatements = Op03SimpleStatement.renumber(outStatements);
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

        return Op03SimpleStatement.removeUnreachableCode(outStatements, true);
    }

    private static boolean stripBackExceptions(List<Op03SimpleStatement> statements) {
        boolean res = false;
        List<Op03SimpleStatement> tryStatements = Functional.filter(statements, new Op03SimpleStatement.ExactTypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement statement : tryStatements) {
            if (statement.getTargets().isEmpty()) continue;
            Op03SimpleStatement fallThrough = statement.getTargets().get(0);
            List<Op03SimpleStatement> backTargets = Functional.filter(statement.getTargets(), new Op03SimpleStatement.IsForwardJumpTo(statement.getIndex()));
            boolean thisRes = false;
            for (Op03SimpleStatement backTarget : backTargets) {
                backTarget.removeSource(statement);
                statement.removeTarget(backTarget);
                thisRes = true;
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

        @Override
        public int compareTo(Block3 other) {
            return startIndex.compareTo(other.startIndex);
        }

        @Override
        public String toString() {
            return "(" + content.size() + ")[" + sources.size() + "/" + originalSources.size() + "," + targets.size() + "] " + getStart().toString();
        }

        private List<Op03SimpleStatement> getContent() {
            return content;
        }

        public void copySources() {
            sources.clear();
            sources.addAll(originalSources);
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
