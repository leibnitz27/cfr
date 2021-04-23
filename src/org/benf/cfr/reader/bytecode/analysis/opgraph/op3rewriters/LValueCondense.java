package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractMutatingAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;

import java.util.List;

public class LValueCondense {
    /*
     * if we have a chain (DIRECTLY CONNECTED) of
     *
     * b = c = d;
     *
     * we'll end up with
     *
     * b = d; c = d   OR   c = d; b = d
     * Then we need to massage them into an assignment chain.
     *
     * Find them by following chains where the RHS is the same.
     */
    public static void condenseLValueChain1(List<Op03SimpleStatement> statements) {

        for (Op03SimpleStatement statement : statements) {
            Statement stm = statement.getStatement();
            if (stm instanceof AssignmentSimple) {
                if (statement.getTargets().size() == 1) {
                    Op03SimpleStatement statement2 = statement.getTargets().get(0);
                    if (statement2.getSources().size() != 1) {
                        continue;
                    }
                    Statement stm2 = statement2.getStatement();
                    if (stm2 instanceof AssignmentSimple) {
                        applyLValueSwap((AssignmentSimple) stm, (AssignmentSimple) stm2, statement, statement2);
                    }
                }
            }
        }
    }

    private static void applyLValueSwap(AssignmentSimple a1, AssignmentSimple a2,
                                        Op03SimpleStatement stm1, Op03SimpleStatement stm2) {
        Expression r1 = a1.getRValue();
        Expression r2 = a2.getRValue();
        if (!r1.equals(r2)) return;
        LValue l1 = a1.getCreatedLValue();
        LValue l2 = a2.getCreatedLValue();

        if ((l1 instanceof StackSSALabel) && !(l2 instanceof StackSSALabel)) {
            stm1.replaceStatement(a2);
            stm2.replaceStatement(new AssignmentSimple(BytecodeLoc.TODO, l1, new LValueExpression(l2)));
        }
    }

    public static void condenseLValueChain2(List<Op03SimpleStatement> statements) {

        for (Op03SimpleStatement statement : statements) {
            Statement stm = statement.getStatement();
            if (stm instanceof AssignmentSimple) {
                if (statement.getTargets().size() == 1) {
                    Op03SimpleStatement statement2 = statement.getTargets().get(0);
                    if (statement2.getSources().size() != 1) {
                        continue;
                    }
                    Statement stm2 = statement2.getStatement();
                    if (stm2 instanceof AssignmentSimple) {
                        applyLValueCondense((AssignmentSimple) stm, (AssignmentSimple) stm2, statement, statement2);
                    }
                }
            }
        }
    }

    private static void applyLValueCondense(AssignmentSimple a1, AssignmentSimple a2,
                                            Op03SimpleStatement stm1, Op03SimpleStatement stm2) {
        Expression r1 = a1.getRValue();
        Expression r2 = a2.getRValue();
        LValue l1 = a1.getCreatedLValue();
        LValue l2 = a2.getCreatedLValue();
        if (!r2.equals(new LValueExpression(l1))) return;


        Expression newRhs = null;
        if (r1 instanceof ArithmeticOperation && ((ArithmeticOperation) r1).isMutationOf(l1)) {
            ArithmeticOperation ar1 = (ArithmeticOperation) r1;
            AbstractMutatingAssignmentExpression me = ar1.getMutationOf(l1);
            newRhs = me;
        }

        if (newRhs == null) newRhs = new AssignmentExpression(BytecodeLoc.TODO, l1, r1);
        /*
         * But only if we have enough type information to know this is ok.
         */
        if (newRhs.getInferredJavaType().getJavaTypeInstance() != l2.getInferredJavaType().getJavaTypeInstance()) {
            return;
        }

        stm2.replaceStatement(new AssignmentSimple(BytecodeLoc.TODO, l2, newRhs));
        stm1.nopOut();
    }



}
