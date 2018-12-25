package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.util.collections.Functional;

import java.util.List;

public class CondenseConditionals {
    /*
     * We look for related groups of conditionals, such that
     *
     * if (c1) then b
     * if (c2) then a
     * b:
     *
     * === if (!c1 && c2) then a
     * b:
     *
     * TODO :
     * /Users/lee/Downloads/jarsrc/com/strobel/decompiler/languages/java/utilities/RedundantCastUtility.class :: processCall.
     *
     * has
     * if (c1) then a
     * if (c2) then b
     * goto a
     *
     * ==>
     *
     * if (c1) then a
     * if (!c2) then a
     * goto b
     *
     * ==>
     *
     * if (c1 || !c2) then a
     * goto b
     *
     * also
     * /Users/lee/code/java/cfr_tests/out/production/cfr_tests/org/benf/cfr/tests/ShortCircuitAssignTest7.class
     */
    public static boolean condenseConditionals(List<Op03SimpleStatement> statements) {
        boolean effect = false;
        for (int x = 0; x < statements.size(); ++x) {
            boolean retry;
            do {
                retry = false;
                Op03SimpleStatement op03SimpleStatement = statements.get(x);
                // If successful, this statement will be nopped out, and the next one will be
                // the combination of the two.
                Statement inner = op03SimpleStatement.getStatement();
                if (!(inner instanceof IfStatement)) continue;
                Op03SimpleStatement fallThrough = op03SimpleStatement.getTargets().get(0);
                Op03SimpleStatement taken = op03SimpleStatement.getTargets().get(1);
                Statement fallthroughInner = fallThrough.getStatement();
                Statement takenInner = taken.getStatement();
                // Is the taken path just jumping straight over the non taken?
                boolean takenJumpBy1 = (x < statements.size() - 2) && statements.get(x+2) == taken;

                if (fallthroughInner instanceof IfStatement) {
                    Op03SimpleStatement sndIf = fallThrough;
                    Op03SimpleStatement sndTaken = sndIf.getTargets().get(1);
                    Op03SimpleStatement sndFallThrough = sndIf.getTargets().get(0);

                    retry = condenseIfs(op03SimpleStatement, sndIf, taken, sndTaken, sndFallThrough, false);

//                    if (if2.condenseWithPriorIfStatement(if1, false)) {
//                        retry = true;
//                    }
                } else if (fallthroughInner.getClass() == GotoStatement.class && takenJumpBy1 && takenInner instanceof IfStatement) {
                    // If it's not an if statement, we might have to negate a jump - i.e.
                    // if (c1) a1
                    // goto a2
                    // a1 : if (c2) goto a2
                    // a3
                    //
                    // is (of course) equivalent to
                    // if (!c1 || c2) goto a2

                    Op03SimpleStatement negatedTaken = fallThrough.getTargets().get(0);
                    Op03SimpleStatement sndIf = statements.get(x+2);
                    Op03SimpleStatement sndTaken = sndIf.getTargets().get(1);
                    Op03SimpleStatement sndFallThrough = sndIf.getTargets().get(0);

                    retry = condenseIfs(op03SimpleStatement, sndIf, negatedTaken, sndTaken, sndFallThrough, true);

//                    IfStatement if1 = (IfStatement)inner;
//                    IfStatement if2 = (IfStatement)takenInner;
//                    if (if2.condenseWithPriorIfStatement(if1, true)) {
//                        retry = true;
//                    }
                }

                if (retry) {
                    effect = true;
                    do {
                        x--;
                    } while (statements.get(x).isAgreedNop() && x > 0);
                }
            } while (retry);
        }
        return effect;
    }


    // if (c1) goto a
    // if (c2) goto b
    // a    (equivalently, GOTO a)
    // ->
    // if (!c1 && c2) goto b

    // if (c1) goto a
    // if (c2) goto a
    // b
    // ->
    // if (c1 || c2) goto a
    private static boolean condenseIfs(Op03SimpleStatement if1, Op03SimpleStatement if2,
                                       Op03SimpleStatement taken1, Op03SimpleStatement taken2, Op03SimpleStatement fall2,
                                       boolean negated1) {
        if (if2.getSources().size() != 1) {
            return false;
        }

        BoolOp resOp;
        boolean negate1;

        if (taken1 == fall2) {
            resOp = BoolOp.AND;
            negate1 = true;
        } else if (taken1 == taken2) {
            resOp = BoolOp.OR;
            negate1 = false;
        } else {
            Statement fall2stm = fall2.getStatement();
            if (fall2stm.getClass() == GotoStatement.class && fall2.getTargets().get(0) == taken1) {
                resOp = BoolOp.AND;
                negate1 = true;
            } else {
                return false;
            }
        }

        ConditionalExpression cond1 = ((IfStatement)if1.getStatement()).getCondition();
        ConditionalExpression cond2 = ((IfStatement)if2.getStatement()).getCondition();
        if (negated1) {
            negate1 = !negate1;
        }
        if (negate1) cond1 = cond1.getNegated();
        ConditionalExpression combined = new BooleanOperation(cond1, cond2, resOp);
        combined = combined.simplify();
        // We need to remove both the targets from the first if, all the sources from the second if (which should be just 1!).
        // Then first if becomes a NOP which points directly to second, and second gets new condition.
        if2.replaceStatement(new IfStatement(combined));

        // HACK - we know this is how nopoutconditional will work.
        for (Op03SimpleStatement target1 : if1.getTargets()){
            target1.removeSource(if1);
        }
        if1.getTargets().clear();
        for (Op03SimpleStatement source1 : if2.getSources()) {
            source1.removeGotoTarget(if2);
        }
        if2.getSources().clear();
        if1.getTargets().add(if2);
        if2.getSources().add(if1);

        if1.nopOutConditional();

        return true;
    }

    public static boolean condenseConditionals2(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        boolean result = false;
        for (Op03SimpleStatement ifStatement : ifStatements) {
            // separated for stepping
            if (condenseConditional2_type1(ifStatement, statements)) {
                result = true;
            } else if (condenseConditional2_type2(ifStatement)) {
                result = true;
            } else if (condenseConditional2_type3(ifStatement, statements)) {
                result = true;
            }
        }
        return result;
    }

    /*

    (b? c : a) || (c ? a : b)
    proves remarkably painful to structure.
    CondTest5*
        
   S1  if (A) GOTO S4
   S2  if (B) GOTO S5
   S3  GOTO Y
   S4  if (C) goto Y
   S5  
         -->

   S1 if (A ? C : !B) GOTO Y
   S2 GOTO S5
   S3 NOP // unjoined
   S4 NOP // unjoined

     */
    private static boolean condenseConditional2_type3(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> allStatements) {
        Op03SimpleStatement s1c = ifStatement;
        Statement s1 = s1c.getStatement();
        if (s1.getClass() != IfStatement.class) return false;
        Op03SimpleStatement s4c = ifStatement.getTargets().get(1);
        Op03SimpleStatement s2c = ifStatement.getTargets().get(0);
        Statement s2 = s2c.getStatement();
        if (s2.getClass() != IfStatement.class) return false;
        Statement s4 = s4c.getStatement();
        if (s4.getClass() != IfStatement.class) return false;
        Op03SimpleStatement s3c = s2c.getTargets().get(0);
        Statement s3 = s3c.getStatement();
        if (s3.getClass() != GotoStatement.class) return false;

        Op03SimpleStatement s5c = s2c.getTargets().get(1);
        Op03SimpleStatement y = s3c.getTargets().get(0);
        if (s4c.getTargets().get(1) != y) return false;
        if (s4c.getTargets().get(0) != s5c) return false;
        if (s2c.getSources().size() != 1) return false;
        if (s3c.getSources().size() != 1) return false;
        if (s4c.getSources().size() != 1) return false;
        IfStatement is1 = (IfStatement)s1;
        IfStatement is2 = (IfStatement)s2;
        IfStatement is4 = (IfStatement)s4;
        ConditionalExpression cond = new BooleanExpression(new TernaryExpression(is1.getCondition(), is4.getCondition(), is2.getCondition().getNegated()));

        s1c.replaceStatement(new IfStatement(cond));
        s1c.replaceTarget(s4c,y);
        y.replaceSource(s4c, s1c);

        // Fix sources / targets.
        s2c.replaceStatement(new GotoStatement());
        s2c.removeGotoTarget(s3c);
        s3c.removeSource(s2c);
        s3c.clear();
        s4c.clear();

        // If we know for a fact that the original nodes were laid out linearly, then we can assume fallthrough from
        // S1 to S5.
        // This violates the "next is fallthrough" invariant temporarily, and is only ok because we KNOW
        // we will tidy up immediately.
        int idx = allStatements.indexOf(s1c);
        if (allStatements.size() > idx+5
                && allStatements.get(idx+1) == s2c
                && allStatements.get(idx+2) == s3c
                && allStatements.get(idx+3) == s4c
                && allStatements.get(idx+4) == s5c) {
            s5c.replaceSource(s2c, s1c);
            s1c.replaceTarget(s2c, s5c);
            s2c.clear();
        }

        return true;
    }

    /*
     * Attempt to find really simple inline ternaries / negations, so we can convert them before conditional rollup.
     */
    private static boolean condenseConditional2_type2(Op03SimpleStatement ifStatement) {
        Statement innerStatement = ifStatement.getStatement();
        if (!(innerStatement instanceof IfStatement)) return false;
        IfStatement innerIf = (IfStatement)innerStatement;
        Op03SimpleStatement tgt1 = ifStatement.getTargets().get(0);
        final Op03SimpleStatement tgt2 = ifStatement.getTargets().get(1);
        if (tgt1.getSources().size() != 1) return false;
        if (tgt2.getSources().size() != 1) return false;
        if (tgt1.getTargets().size() != 1) return false;
        if (tgt2.getTargets().size() != 1) return false;
        Op03SimpleStatement evTgt = tgt1.getTargets().get(0);
        evTgt = Misc.followNopGoto(evTgt, true, false);
        Op03SimpleStatement oneSource = tgt1;
        if (!(evTgt.getSources().contains(oneSource) || evTgt.getSources().contains(oneSource = oneSource.getTargets().get(0)))) {
            return false;
        }
        if (evTgt.getSources().size() < 2) return false; // FIXME.  Shouldnt' clear, below.
        if (tgt2.getTargets().get(0) != evTgt) return false; // asserted tgt2 is a source of evTgt.
        Statement stm1 = tgt1.getStatement();
        Statement stm2 = tgt2.getStatement();
        if (!(stm1 instanceof AssignmentSimple && stm2 instanceof AssignmentSimple)) {
            return false;
        }
        AssignmentSimple a1 = (AssignmentSimple)stm1;
        AssignmentSimple a2 = (AssignmentSimple)stm2;
        LValue lv = a1.getCreatedLValue();
        if (!lv.equals(a2.getCreatedLValue())) return false;
        ConditionalExpression condition = innerIf.getCondition().getNegated();
        condition = condition.simplify();
        ifStatement.replaceStatement(new AssignmentSimple(lv, new TernaryExpression(condition, a1.getRValue(), a2.getRValue())));
        ifStatement.getSSAIdentifiers().consumeEntry(evTgt.getSSAIdentifiers());
        oneSource.replaceStatement(new Nop());
        oneSource.removeTarget(evTgt);
        tgt2.replaceStatement(new Nop());
        tgt2.removeTarget(evTgt);
        evTgt.removeSource(oneSource);
        evTgt.removeSource(tgt2);
        evTgt.getSources().add(ifStatement);
        for (Op03SimpleStatement tgt : ifStatement.getTargets()) {
            tgt.removeSource(ifStatement);
        }
        ifStatement.getTargets().clear();
        ifStatement.addTarget(evTgt);
        tgt1.replaceStatement(new Nop());
        // Reduce count, or lvalue condensing won't work.
        if (lv instanceof StackSSALabel) {
            ((StackSSALabel) lv).getStackEntry().decSourceCount();
        }
        return true;
    }


    /*
     * Look for a very specific pattern which is really awkward to pull out later
     *
     * if (w) goto a [taken1]                  [ifstatement1]
     * if (x) goto b [taken2]                  [ifstatement2]
     * [nottaken2] goto c [nottaken3]
     * [taken1] a: if (z) goto b  [taken3]     [ifstatement3]
     * [nottaken3] c:
     * ....
     * b:
     *
     *
     * if ((w && z) || x) goto b
     * goto c:
     *
     * c:
     */
    private static boolean condenseConditional2_type1(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> allStatements) {
        if (!(ifStatement.getStatement() instanceof IfStatement)) return false;

        final Op03SimpleStatement taken1 = ifStatement.getTargets().get(1);
        final Op03SimpleStatement nottaken1 = ifStatement.getTargets().get(0);
        if (!(nottaken1.getStatement() instanceof IfStatement)) return false;
        Op03SimpleStatement ifStatement2 = nottaken1;
        Op03SimpleStatement taken2 = ifStatement2.getTargets().get(1);
        Op03SimpleStatement nottaken2 = ifStatement2.getTargets().get(0);
        final Op03SimpleStatement nottaken2Immed = nottaken2;
        if (nottaken2Immed.getSources().size() != 1) return false;
        nottaken2 = Misc.followNopGotoChain(nottaken2, true, false);
        do {
            Op03SimpleStatement nontaken2rewrite = Misc.followNopGoto(nottaken2, true, false);
            if (nontaken2rewrite == nottaken2) break;
            nottaken2 = nontaken2rewrite;
        } while (true);
        if (!(taken1.getStatement() instanceof IfStatement)) return false;
        if (taken1.getSources().size() != 1) return false;
        Op03SimpleStatement ifStatement3 = taken1;
        Op03SimpleStatement taken3 = ifStatement3.getTargets().get(1);
        Op03SimpleStatement nottaken3 = ifStatement3.getTargets().get(0);
        Op03SimpleStatement notTaken3Source = ifStatement3;
        do {
            Op03SimpleStatement nontaken3rewrite = Misc.followNopGoto(nottaken3, true, false);
            if (nontaken3rewrite == nottaken3) break;
            notTaken3Source = nottaken3;
            nottaken3 = nontaken3rewrite;
        } while (true);

        // nottaken2 = nottaken3 = c
        if (nottaken2 != nottaken3) {
            // There's one final thing we can get away with - if these are both returns, and they are IDENTICAL
            // (i.e. same, AND for any variables accessed, ssa-same), then we can assume nottaken2 is a rewritten branch
            // to nottaken3.
            if (nottaken2.getStatement() instanceof ReturnStatement) {
                if (!nottaken2.getStatement().equivalentUnder(nottaken3.getStatement(), new StatementEquivalenceConstraint(nottaken2, nottaken3))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        if (taken2 != taken3) return false; // taken2 = taken3 = b;
        /*
         * rewrite as if ((w && z) || x)
         *
         *
         */
        IfStatement if1 = (IfStatement) ifStatement.getStatement();
        IfStatement if2 = (IfStatement) ifStatement2.getStatement();
        IfStatement if3 = (IfStatement) ifStatement3.getStatement();

        ConditionalExpression newCond = new BooleanExpression(
                new TernaryExpression(
                        if1.getCondition().getNegated().simplify(),
                        if2.getCondition().getNegated().simplify(),
                        if3.getCondition().getNegated().simplify())).getNegated();
//        TernaryExpression
//        ConditionalExpression newCond =
//                new BooleanOperation(
//                        new BooleanOperation(if1.getCondition(), if2.getCondition(), BoolOp.OR),
//                        if3.getCondition(), BoolOp.AND);

        ifStatement.replaceTarget(taken1, taken3);
        taken3.addSource(ifStatement);
        taken3.removeSource(ifStatement2);
        taken3.removeSource(ifStatement3);

        nottaken1.getSources().remove(ifStatement);
        nottaken2Immed.replaceSource(ifStatement2, ifStatement);
        ifStatement.replaceTarget(nottaken1, nottaken2Immed);

//        nottaken3.removeSource(notTaken3Source);
        nottaken3.removeSource(notTaken3Source);

        ifStatement2.replaceStatement(new Nop());
        ifStatement3.replaceStatement(new Nop());
        ifStatement2.removeTarget(taken3);
        ifStatement3.removeTarget(taken3);

        ifStatement.replaceStatement(new IfStatement(newCond));

        /*
         * Now we're cleared up, see if nottaken2immed actually jumps straight to its target.
         */
        if (nottaken2Immed.getSources().size() == 1) {
            if (nottaken2Immed.getSources().get(0).getIndex().isBackJumpFrom(nottaken2Immed)) {
                if (nottaken2Immed.getStatement().getClass() == GotoStatement.class) {
                    Op03SimpleStatement nottaken2ImmedTgt = nottaken2Immed.getTargets().get(0);
                    int idx = allStatements.indexOf(nottaken2Immed);
                    int idx2 = idx + 1;
                    do {
                        Op03SimpleStatement next = allStatements.get(idx2);
                        if (next.getStatement() instanceof Nop) {
                            idx2++;
                            continue;
                        }
                        if (next == nottaken2ImmedTgt) {
                            // Replace nottaken2 with nottaken2ImmedTgt.
                            nottaken2ImmedTgt.replaceSource(nottaken2Immed, ifStatement);
                            ifStatement.replaceTarget(nottaken2Immed, nottaken2ImmedTgt);
                        }
                        break;
                    } while (true);
                }
            }
        }

        return true;

    }

}
