package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Predicate;

import java.util.List;

public class AnonymousArray {


    private static boolean resugarAnonymousArray(Op03SimpleStatement newArray, List<Op03SimpleStatement> statements) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) newArray.getStatement();
        WildcardMatch start = new WildcardMatch();
        if (!start.match(
                new AssignmentSimple(start.getLValueWildCard("array"), start.getNewArrayWildCard("def")),
                assignmentSimple
        )) {
            throw new ConfusedCFRException("Expecting new array");
        }
        /*
         * If it's not a literal size, ignore.
         */
        LValue arrayLValue = start.getLValueWildCard("array").getMatch();
        if (!(arrayLValue instanceof StackSSALabel || arrayLValue instanceof LocalVariable)) {
            return false;
        }
        LValue array = arrayLValue;
        AbstractNewArray arrayDef = start.getNewArrayWildCard("def").getMatch();
        Expression dimSize0 = arrayDef.getDimSize(0);
        if (!(dimSize0 instanceof Literal)) return false;
        Literal lit = (Literal) dimSize0;
        if (lit.getValue().getType() != TypedLiteral.LiteralType.Integer) return false;
        int bound = (Integer) lit.getValue().getValue();

        Op03SimpleStatement next = newArray;
        List<Expression> anon = ListFactory.newList();
        List<Op03SimpleStatement> anonAssigns = ListFactory.newList();
        Expression arrayExpression = null;
        if (array instanceof StackSSALabel) {
            arrayExpression = new StackValue((StackSSALabel) array);
        } else {
            arrayExpression = new LValueExpression(array);
        }
        for (int x = 0; x < bound; ++x) {
            if (next.getTargets().size() != 1) {
                return false;
            }
            next = next.getTargets().get(0);
            WildcardMatch testAnon = new WildcardMatch();
            Literal idx = new Literal(TypedLiteral.getInt(x));
            if (!testAnon.match(
                    new AssignmentSimple(
                            new ArrayVariable(new ArrayIndex(arrayExpression, idx)),
                            testAnon.getExpressionWildCard("val")),
                    next.getStatement())) {
                return false;
            }
            anon.add(testAnon.getExpressionWildCard("val").getMatch());
            anonAssigns.add(next);
        }
        AssignmentSimple replacement = new AssignmentSimple(arrayLValue.getInferredJavaType(), assignmentSimple.getCreatedLValue(), new NewAnonymousArray(arrayDef.getInferredJavaType(), arrayDef.getNumDims(), anon, false));
        newArray.replaceStatement(replacement);
        if (array instanceof StackSSALabel) {
            StackEntry arrayStackEntry = ((StackSSALabel) array).getStackEntry();
            for (Op03SimpleStatement create : anonAssigns) {
                arrayStackEntry.decrementUsage();
            }
        }
        for (Op03SimpleStatement create : anonAssigns) {
            create.nopOut();
        }
        return true;
    }


    /*
     * Search for
     *
     * stk = new X[N];
     * stk[0] = a
     * stk[1] = b
     * ...
     * stk[N-1] = c
     *
     * transform into stk = new X{ a,b, .. c }
     *
     * (it's important that stk is a stack label, so we don't allow an RValue to reference it inside the
     * array definition!)
     */
    public static void resugarAnonymousArrays(List<Op03SimpleStatement> statements) {
        boolean success = false;
        do {
            List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
            // filter for structure now
            assignments = Functional.filter(assignments, new Predicate<Op03SimpleStatement>() {
                @Override
                public boolean test(Op03SimpleStatement in) {
                    AssignmentSimple assignmentSimple = (AssignmentSimple) in.getStatement();
                    WildcardMatch wildcardMatch = new WildcardMatch();
                    return (wildcardMatch.match(
                            new AssignmentSimple(wildcardMatch.getLValueWildCard("array"), wildcardMatch.getNewArrayWildCard("def", 1, null)),
                            assignmentSimple
                    ));
                }
            });
            success = false;
            for (Op03SimpleStatement assignment : assignments) {
                success |= resugarAnonymousArray(assignment, statements);
            }
            if (success) {
                LValueProp.condenseLValues(statements);
            }
        }
        while (success);
    }}
