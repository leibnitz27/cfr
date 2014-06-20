package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.SetUtil;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SynchronizedBlocks {
    /*
    * We make a (dangerous?) assumption here - that the monitor entered is the same one as exited.
    * Can JVM spec be read to allow
    *
    * a = x;
    * b = x;
    * enter(a)
    * exit(b) ?
    *
    * Since monitorenter/exit must be paired (it's counted) we don't have to worry (much!) about monitorenter in a loop without
    * exit.
    *
    * (might be a good anti-decompiler technique though!)
    *
    * What would be nasty is a switch statement which enters on one branch and exits on another...
    */
    public static void findSynchronizedBlocks(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> enters = Functional.filter(statements, new TypeFilter<MonitorEnterStatement>(MonitorEnterStatement.class));
        // Each exit can be tied to one enter, which is the first one found by
        // walking code backwards and not passing any other exit/enter for this var.
        // (Every exit from a synchronised block has to exit, so if there's any possibiliy of an exception... )

        for (Op03SimpleStatement enter : enters) {
            MonitorEnterStatement monitorExitStatement = (MonitorEnterStatement) enter.getStatement();

            findSynchronizedRange(enter, monitorExitStatement.getMonitor());
        }
    }

    public static void findSynchronizedRange(final Op03SimpleStatement start, final Expression monitor) {
        final Set<Op03SimpleStatement> addToBlock = SetFactory.newSet();

        final Set<Op03SimpleStatement> foundExits = SetFactory.newSet();
        final Set<Op03SimpleStatement> extraNodes = SetFactory.newSet();
        /* Process all the parents until we find the monitorExit.
         * Note that this does NOT find statements which are 'orphaned', i.e.
         *
         * synch(foo) {
         *   try {
         *     bob
         *   } catch (e) {
         *     throw  <--- not reachable backwards from monitorexit,
         *   }
         *   monitorexit.
         *
         *   However, there must necessarily be a monitorexit before this throw.
         * }
         */

        final Set<BlockIdentifier> leaveExitsMutex = SetFactory.newSet();

        GraphVisitor<Op03SimpleStatement> marker = new GraphVisitorDFS<Op03SimpleStatement>(start.getTargets(),
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        //
                        //

                        Statement statement = arg1.getStatement();

                        if (statement instanceof TryStatement) {
                            TryStatement tryStatement = (TryStatement) statement;
                            Set<Expression> tryMonitors = tryStatement.getMonitors();
                            if (tryMonitors.contains(monitor)) {
                                leaveExitsMutex.add(tryStatement.getBlockIdentifier());
                                List<Op03SimpleStatement> tgts = arg1.getTargets();
                                for (int x = 1, len = tgts.size(); x < len; ++x) {
                                    Statement innerS = tgts.get(x).getStatement();
                                    if (innerS instanceof CatchStatement) {
                                        leaveExitsMutex.add(((CatchStatement) innerS).getCatchBlockIdent());
                                    } else if (innerS instanceof FinallyStatement) {
                                        leaveExitsMutex.add(((FinallyStatement) innerS).getFinallyBlockIdent());
                                    }
                                }
                            }
                        }

                        if (statement instanceof MonitorExitStatement) {
                            if (monitor.equals(((MonitorExitStatement) statement).getMonitor())) {
                                foundExits.add(arg1);
                                addToBlock.add(arg1);
                                /*
                                 * If there's a return / throw / goto immediately after this, then we know that the brace
                                 * is validly moved.
                                 */
                                if (arg1.getTargets().size() == 1) {
                                    arg1 = arg1.getTargets().get(0);
                                    Statement targetStatement = arg1.getStatement();
                                    if (targetStatement instanceof ReturnStatement ||
                                            targetStatement instanceof ThrowStatement ||
                                            targetStatement instanceof Nop ||
                                            targetStatement instanceof GotoStatement) {
                                        // TODO : Should perform a block check on targetStatement.
                                        extraNodes.add(arg1);
                                    }
                                }

                                return;
                            }
                        }
                        addToBlock.add(arg1);
                        if (SetUtil.hasIntersection(arg1.getBlockIdentifiers(), leaveExitsMutex)) {
                            for (Op03SimpleStatement tgt : arg1.getTargets()) {
                                if (SetUtil.hasIntersection(tgt.getBlockIdentifiers(), leaveExitsMutex)) {
                                    arg2.enqueue(tgt);
                                }
                            }
                        } else {
                            arg2.enqueue(arg1.getTargets());
                        }
                    }
                }
        );
        marker.process();

        /*
         * An extra pass, wherein we find all blocks which members of addtoblock are in.
         * (and the initial start is not in.)
         * This is because we need to handle synchronized blocks which may not fit into the natural
         * ordering.
         *
         * monitorenter
         * bip
         * if
         *   foo
         *   monitorexit
         *   bar
         * else
         *   bop
         *   monitorexit
         *   bam
         */
        addToBlock.remove(start);
        /*
         * find entries with same-block targets which are NOT in addToBlock, add them.
         */
        Set<Op03SimpleStatement> requiredComments = SetFactory.newSet();
        Iterator<Op03SimpleStatement> foundExitIter = foundExits.iterator();
        while (foundExitIter.hasNext()) {
            final Op03SimpleStatement foundExit = foundExitIter.next();
            final Set<BlockIdentifier> exitBlocks = SetFactory.newSet(foundExit.getBlockIdentifiers());
            exitBlocks.removeAll(start.getBlockIdentifiers());
            final List<Op03SimpleStatement> added = ListFactory.newList();
            GraphVisitor<Op03SimpleStatement> additional = new GraphVisitorDFS<Op03SimpleStatement>(foundExit, new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                @Override
                public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                    if (SetUtil.hasIntersection(exitBlocks, arg1.getBlockIdentifiers())) {
                        if (arg1 == foundExit) {
                            arg2.enqueue(arg1.getTargets());
                        } else if (addToBlock.add(arg1)) {
                            added.add(arg1);
                            arg2.enqueue(arg1.getTargets());
                        }
                    }
                }
            });
            additional.process();
            // If we had an effect, then we want to redesignate this monitor exit as a 'required comment'
            if (anyOpHasEffect(added)) {
                requiredComments.add(foundExit);
                foundExitIter.remove();
            }
        }


        MonitorEnterStatement monitorEnterStatement = (MonitorEnterStatement) (start.getStatement());
        BlockIdentifier blockIdentifier = monitorEnterStatement.getBlockIdentifier();
        for (Op03SimpleStatement contained : addToBlock) {
            contained.getBlockIdentifiers().add(blockIdentifier);
        }

        for (Op03SimpleStatement exit : foundExits) {
            exit.nopOut();
        }

        for (Op03SimpleStatement exit : requiredComments) {
            exit.replaceStatement(new CommentStatement("MONITOREXIT " + exit));
        }

        /* For the extra nodes, if ALL the sources are in the block, we add the extranode
         * to the block.  This pulls returns/throws into the block, but keeps them out
         * if they're targets for a conditional outside the block.
         */
        for (Op03SimpleStatement extra : extraNodes) {
            boolean allParents = true;
            for (Op03SimpleStatement source : extra.getSources()) {
                if (!source.getBlockIdentifiers().contains(blockIdentifier)) {
                    allParents = false;
                }
            }
            if (allParents) {
                extra.getBlockIdentifiers().add(blockIdentifier);
            }
        }


    }



    /*
     * Strictly speaking, this isn't true.....  We should verify elsewhere first that we don't push
     * operations through a monitorexit, as that will change the semantics.
     */
    private static boolean anyOpHasEffect(List<Op03SimpleStatement> ops) {
        for (Op03SimpleStatement op : ops) {
            Statement stm = op.getStatement();
            Class<?> stmcls = stm.getClass();
            if (stmcls == GotoStatement.class) continue;
            if (stmcls == ThrowStatement.class) continue;
            if (stmcls == CommentStatement.class) continue;
            if (stm instanceof ReturnStatement) continue;
            return true;
        }
        return false;
    }

}
