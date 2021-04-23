package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BoolOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CompOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ComparisonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NotOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.DoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * Consider the (EXTREMELY unlikely, but occasional)
 *
 * if (FOO) goto XXX
 * do
 *   a
 *   b
 *   XXX
 *   c
 * WHILE (d)
 *
 * We want to spot this pattern fairly early, as it prohibits structuring.
 *
 * Ideally we'd know FOO was side effect free, and could only be true on the first iteration and we'd just do this:
 *
 * do
 *   if (FOO) goto XXX
 *   a
 *   b
 *   XXX
 *   c
 * WHILE (d)
 *
 * However that's pretty unlikely.  So we do this (and apologise;).
 *
 * first = true;
 * do
 *   if (first && !(first = false) && FOO) goto XXX
 *   a
 *   b
 *   XXX
 *   c
 * WHILE (d)
 *
 * Note - the alternative here is to extract the bottom half of the loop, and rotate the loop around it.
 *
 * if (!FOO) {
 *   a
 *   b
 * }
 * do {
 *   c
 *   if (!d) break;
 *   a
 *   b
 * } while (true)
 *
 * I haven't (yet) implemented this - it can produce *awful* code, and if there are multiple entry points,
 * can cause enormous duplication.
 */
public class JumpsIntoDoRewriter {

    private final VariableFactory vf;
    private boolean effect;

    JumpsIntoDoRewriter(VariableFactory vf) {
        this.vf = vf;
    }

    private boolean maybeRewriteImmediate(List<Op03SimpleStatement> op03SimpleParseNodes, int x) {
        Op03SimpleStatement doS = op03SimpleParseNodes.get(x);
        Statement doStatement = doS.getStatement();
        if (doStatement instanceof DoStatement) {
            Op03SimpleStatement prev = op03SimpleParseNodes.get(x-1);
            Statement prevStm = prev.getStatement();
            BlockIdentifier doBlockIdentifier = ((DoStatement) doStatement).getBlockIdentifier();
            if (prevStm instanceof IfStatement && prev.getTargets().get(1).getBlockIdentifiers().contains(doBlockIdentifier)) {
                Op03SimpleStatement prevTgt = prev.getTargets().get(1);
                // it's implicit that the fall through is the do statement.
                Set<BlockIdentifier> prevTgtIdents = prevTgt.getBlockIdentifiers();
                Set<BlockIdentifier> ifStmIdents = prev.getBlockIdentifiers();
                // The two must be the same other than this ONE identifier.
                if (!(prevTgtIdents.size() == ifStmIdents.size() + 1 &&
                    prevTgtIdents.containsAll(ifStmIdents) &&
                    !ifStmIdents.contains(doBlockIdentifier))) {
                    return false;
                }

                Op03SimpleStatement afterDo = doS.getTargets().get(0);
                IfStatement prevIf = (IfStatement)prevStm;
                SSAIdentifiers doId = doS.getSSAIdentifiers();
                LValue loopControl = vf.tempVariable(new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.TRANSFORM, true));
                prev.replaceStatement(new AssignmentSimple(BytecodeLoc.TODO, loopControl, Literal.TRUE));
                // No, it's not pretty.  But it structures!
                IfStatement newIf = new IfStatement(BytecodeLoc.TODO, new BooleanOperation(BytecodeLoc.TODO,
                        new BooleanOperation(BytecodeLoc.TODO, new ComparisonOperation(BytecodeLoc.TODO, new LValueExpression(loopControl), Literal.TRUE, CompOp.EQ), new NotOperation(BytecodeLoc.TODO, new BooleanExpression(new AssignmentExpression(BytecodeLoc.TODO, loopControl, Literal.FALSE))), BoolOp.AND),
                    prevIf.getCondition(),
                    BoolOp.AND));
                prevTgt.removeSource(prev);
                prev.removeTarget(prevTgt);
                Set<BlockIdentifier> newBlocks = SetFactory.newSet(doS.getBlockIdentifiers());
                newBlocks.add(doBlockIdentifier);
                Op03SimpleStatement newStm = new Op03SimpleStatement(newBlocks, newIf, doId, doS.getIndex().justAfter());
                for (Op03SimpleStatement prevSource : ListFactory.newList(afterDo.getSources())) {
                    prevSource.replaceTarget(afterDo, newStm);
                    newStm.addSource(prevSource);
                }
                afterDo.getSources().clear();
                afterDo.addSource(newStm);
                newStm.addTarget(afterDo);
                newStm.addTarget(prevTgt);
                prevTgt.addSource(newStm);
                op03SimpleParseNodes.add(newStm);
                newStm.markFirstStatementInBlock(doBlockIdentifier);
                afterDo.markFirstStatementInBlock(null);
                effect = true;
                return true;
            }
        }
        return false;
    }


    public void rewrite(List<Op03SimpleStatement> op03SimpleParseNodes, DecompilerComments comments) {
        outer: for (int idx=op03SimpleParseNodes.size()-1;idx>=0;--idx) {
            Op03SimpleStatement stm = op03SimpleParseNodes.get(idx);
            if (stm.getStatement() instanceof DoStatement) {
                Set<Op03SimpleStatement> externals = SetFactory.newIdentitySet();
                BlockIdentifier doBlock = ((DoStatement) stm.getStatement()).getBlockIdentifier();
                Set<BlockIdentifier> originalDoIdentifiers = SetFactory.newSet(stm.getBlockIdentifiers());
                for (int z = idx+1; z<op03SimpleParseNodes.size(); ++z) {
                    Op03SimpleStatement s2 = op03SimpleParseNodes.get(z);
                    if (!s2.getBlockIdentifiers().contains(doBlock)) break;
                    for (Op03SimpleStatement source : s2.getSources()) {
                        if (!source.getBlockIdentifiers().contains(doBlock) && source != stm) {
                            // We have to be pretty paranoid - this can only be in the same blocks as the do Statement.
                            if (!source.getBlockIdentifiers().equals(originalDoIdentifiers)) {
                                continue outer;
                            }
                            externals.add(source);
                        }
                    }
                }
                if (!externals.isEmpty()) {
                    List<Op03SimpleStatement> extList = ListFactory.newList(externals);
                    Collections.sort(extList, new CompareByIndex());
                    if (extList.size() == 1) {
                        if (maybeRewriteImmediate(op03SimpleParseNodes, idx)) continue;
                    }
                    Op03SimpleStatement first = extList.get(0);
                    int fistIdx = op03SimpleParseNodes.indexOf(first);
                    if (fistIdx > idx) continue;
                    Set<Op03SimpleStatement> candidates = SetFactory.newIdentitySet();
                    for (int i=fistIdx;i<=idx;++i) {
                        candidates.add(op03SimpleParseNodes.get(i));
                    }
                    for (Op03SimpleStatement doSource : stm.getSources()) {
                        if (doSource.getBlockIdentifiers().contains(doBlock)) continue;
                        if (candidates.contains(doSource)) continue;
                        continue outer;
                    }
                    Op03SimpleStatement newDo = new Op03SimpleStatement(first.getBlockIdentifiers(), new DoStatement(BytecodeLoc.TODO, doBlock), first.getSSAIdentifiers(), first.getIndex().justBefore());
                    Op03SimpleStatement afterDo = stm.getTargets().get(0);

                    stm.replaceStatement(new Nop());
                    for (Op03SimpleStatement candidate : candidates) {
                        candidate.getBlockIdentifiers().add(doBlock);
                    }
                    LValue loopControl = vf.tempVariable(new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.TRANSFORM, true));
                    Op03SimpleStatement preDo = new Op03SimpleStatement(originalDoIdentifiers, new AssignmentSimple(BytecodeLoc.TODO, loopControl, Literal.TRUE), first.getSSAIdentifiers(), newDo.getIndex().justBefore());
                    for (Op03SimpleStatement doSource : ListFactory.newList(stm.getSources())) {
                        if (!candidates.contains(doSource)) {
                            doSource.replaceTarget(stm, newDo);
                            stm.removeSource(doSource);
                            newDo.addSource(doSource);
                        }
                    }
                    for (Op03SimpleStatement firstSource : first.getSources()) {
                        firstSource.replaceTarget(first, preDo);
                        preDo.addSource(firstSource);
                    }
                    preDo.addTarget(newDo);
                    newDo.addSource(preDo);
                    first.getSources().clear();


                    // No, it's not pretty.  But it structures!
                    IfStatement newIf = new IfStatement(BytecodeLoc.TODO, new NotOperation(BytecodeLoc.TODO, new BooleanOperation(BytecodeLoc.TODO, new ComparisonOperation(BytecodeLoc.TODO, new LValueExpression(loopControl), Literal.TRUE, CompOp.EQ), new NotOperation(BytecodeLoc.TODO, new BooleanExpression(new AssignmentExpression(BytecodeLoc.TODO, loopControl, Literal.FALSE))), BoolOp.AND)));
                    Op03SimpleStatement newIfStm = new Op03SimpleStatement(first.getBlockIdentifiers(), newIf, first.getSSAIdentifiers(), newDo.getIndex().justAfter());

                    newIfStm.addSource(newDo);
                    newDo.addTarget(newIfStm);
                    newIfStm.markFirstStatementInBlock(doBlock);
                    first.addSource(newIfStm);
                    newIfStm.addTarget(first);
                    newIfStm.addTarget(stm);
                    stm.addSource(newIfStm);
                    op03SimpleParseNodes.add(preDo);
                    op03SimpleParseNodes.add(newDo);
                    op03SimpleParseNodes.add(newIfStm);
                    afterDo.markFirstStatementInBlock(null);
                    effect = true;
                }
            }
        }
        if (effect) {
            // This is going to generate pretty grotty code, (see LoopFakery tests), so we want to
            // apologise....
            comments.addComment(DecompilerComment.IMPOSSIBLE_LOOP_WITH_FIRST);
            Cleaner.sortAndRenumberInPlace(op03SimpleParseNodes);
        }
    }
}
