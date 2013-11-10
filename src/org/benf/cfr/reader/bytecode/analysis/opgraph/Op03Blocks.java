package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
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
    public static List<Op03SimpleStatement> topologicalSort(final Method method, final List<Op03SimpleStatement> statements) {
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

        /*
         * Heuristic - we don't want to reorder entries which leave known blocks - SO... if a source
         * is in a different blockset, we have to wait until the previous block is emitted.
         */
        Block3 linPrev = null;
        for (Block3 block : blocks) {
            Op03SimpleStatement start = block.getStart();
            Set<BlockIdentifier> startIdents = start.getBlockIdentifiers();
            boolean needLinPrev = false;
            for (Block3 source : block.sources) {
                if (!source.getEnd().getBlockIdentifiers().equals(startIdents)) {
                    needLinPrev = true;
                    break;
                }
            }
            if (needLinPrev) {
                block.addSource(linPrev);
            }
            linPrev = block;
        }

        /*
         * Topological sort, preferring earlier.
         *
         * We can't do a naive 'emit with 0 parents because of loops.
         */
        LinkedHashSet<Block3> allBlocks = new LinkedHashSet<Block3>();
        allBlocks.addAll(blocks);


        /* in a simple top sort, you take a node with 0 parents, emit it, remove it as parent from all
         * its children, rinse and repeat.  We can't do that immediately, because we have cycles.
         *
         * v1 - try simple top sort, but when we have no candidates, emit next candidate with only existing
         * later parents.
         */
        Set<Block3> ready = new TreeSet<Block3>();
        ready.add(blocks.get(0));

        List<Block3> output = ListFactory.newList(blocks.size());

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
                 * Take first of others.
                 */
                next = allBlocks.iterator().next();
            }
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

        /*
         * Now, we have to patch up with gotos anywhere that we've changed the original ordering.
         * NB. This is the first destructive change.
         */
        for (int i = 0, len = output.size(); i < len - 1; ++i) {
            Block3 thisBlock = output.get(i);
            Block3 nextBlock = output.get(i + 1);
            patch(thisBlock, nextBlock);
        }

        /*
         * Now go through, and emit the content, in order.
         */
        List<Op03SimpleStatement> outStatements = ListFactory.newList();
        for (Block3 outBlock : output) {
            outStatements.addAll(outBlock.getContent());
        }

        int newIndex = 0;
        for (Op03SimpleStatement statement : outStatements) {
            statement.setIndex(new InstrIndex(newIndex++));
        }

        /*
         * Patch up conditionals.
         */
        for (int x = 0, len = outStatements.size() - 1; x < len; ++x) {
            Op03SimpleStatement stm = outStatements.get(x);
            if (stm.getStatement().getClass() == IfStatement.class) {
                List<Op03SimpleStatement> targets = stm.getTargets();
                Op03SimpleStatement next = outStatements.get(x + 1);
                if (targets.get(0) == next) {
                    // Nothing.
                } else if (targets.get(1) == next) {
                    Op03SimpleStatement a = targets.get(0);
                    Op03SimpleStatement b = targets.get(1);
                    targets.set(0, b);
                    targets.set(1, a);
                } else {
                    throw new IllegalStateException("Failed topsort - if lost targets");
                }
            }
        }

        return Op03SimpleStatement.removeUnreachableCode(outStatements);
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
        if (fallThroughTarget == b.getStart()) return;

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
        List<Block3> sources = ListFactory.newList();
        List<Block3> targets = ListFactory.newList();


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
        }

        public void addSource(Block3 source) {
            this.sources.add(source);
        }

        public void setTargets(List<Block3> targets) {
            this.targets = targets;
        }

        @Override
        public int compareTo(Block3 other) {
            return startIndex.compareTo(other.startIndex);
        }

        @Override
        public String toString() {
            return getStart().toString();
        }

        private List<Op03SimpleStatement> getContent() {
            return content;
        }
    }

}
