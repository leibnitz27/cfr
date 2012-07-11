package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StackValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Assignment;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.NonaryFunction;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 18:06
 * To change this template use File | Settings | File Templates.
 */
public class LValueAssignmentCollector implements LValueRewriter {

    //
    // Found states that key can be replaced with value.
    //
    private final Map<StackSSALabel, ExpressionStatement> found = MapFactory.newMap();
    //
    //
    //
    private final Map<StackSSALabel, Expression> aliasReplacements = MapFactory.newMap();

    // When we know that this value is being used multiple times.
    // Maybe we can convert
    // v10 = 1+1
    // c = v10
    // d = v10
    // into
    // c = 1+1
    // d = c
    private final Map<StackSSALabel, ExpressionStatement> multiFound = MapFactory.newMap();


    public void collect(StackSSALabel lValue, StatementContainer statementContainer, Expression value) {
        found.put(lValue, new ExpressionStatement(value, statementContainer));
    }

    public void collectMultiUse(StackSSALabel lValue, StatementContainer statementContainer, Expression value) {
        multiFound.put(lValue, new ExpressionStatement(value, statementContainer));
    }

    @Override
    public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer lvSc) {
        if (!(lValue instanceof StackSSALabel)) return null;

        StackSSALabel stackSSALabel = (StackSSALabel) lValue;

        if (!found.containsKey(stackSSALabel)) return null;
        ExpressionStatement pair = found.get(stackSSALabel);
        // res is a valid replacement for lValue in an rValue, IF no mutable fields have different version
        // identifiers (SSA tags)
        StatementContainer statementContainer = pair.statementContainer;
        SSAIdentifiers replacementIdentifiers = statementContainer == null ? null : statementContainer.getSSAIdentifiers();
        // We're saying we can replace lValue with res.
        // This is only valid if res has a single possible value in ssaIdentifiers, and it's the same as in replacementIdentifiers.
        Expression res = pair.expression;
        Expression prev = null;
        if (res instanceof LValueExpression && replacementIdentifiers != null) {
            LValue resLValue = ((LValueExpression) res).getLValue();
            if (!ssaIdentifiers.isValidReplacement(resLValue, replacementIdentifiers)) return null;
        }
        if (statementContainer != null) {
            lvSc.copyBlockInformationFrom(statementContainer);
            statementContainer.nopOut();
        }
        stackSSALabel.getStackEntry().decrementUsage();
        if (aliasReplacements.containsKey(stackSSALabel)) {
            found.put(stackSSALabel, new ExpressionStatement(aliasReplacements.get(stackSSALabel), null));
            aliasReplacements.remove(stackSSALabel);
        }
        do {
            prev = res;
            res = res.replaceSingleUsageLValues(this, ssaIdentifiers, lvSc);
//            System.out.println("can replace " + prev + " with " + res);
        } while (res != null && res != prev);
        return prev;
    }

    @Override
    public boolean explicitlyReplaceThisLValue(LValue lValue) {
        return false;
    }

    private static class ExpressionStatement {
        private final Expression expression;
        private final StatementContainer statementContainer;

        private ExpressionStatement(Expression expression, StatementContainer statementContainer) {
            this.expression = expression;
            this.statementContainer = statementContainer;
        }
    }

    public FirstPassRewriter getFirstPassRewriter() {
        return new FirstPassRewriter();
    }

    public class FirstPassRewriter implements LValueRewriter {
        private final Map<StackSSALabel, List<StatementContainer>> usages = MapFactory.newLazyMap(
                new NonaryFunction<List<StatementContainer>>() {
                    @Override
                    public List<StatementContainer> invoke() {
                        return ListFactory.newList();
                    }
                }
        );
        private final Map<StackSSALabel, List<LValueStatementContainer>> possibleAliases = MapFactory.newLazyMap(
                new NonaryFunction<List<LValueStatementContainer>>() {
                    @Override
                    public List<LValueStatementContainer> invoke() {
                        return ListFactory.newList();
                    }
                }
        );

        @Override
        public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
            if (!(lValue instanceof StackSSALabel)) return null;
            StackSSALabel stackSSALabel = (StackSSALabel) lValue;

            if (!multiFound.containsKey(lValue)) return null;
            /* If it's an assignment, then put it in the 'possible alias'
             * list.
             */
            if (statementContainer.getStatement() instanceof Assignment) {
                Assignment assignment = (Assignment) (statementContainer.getStatement());
                Expression rhs = assignment.getRValue();
                if (rhs instanceof StackValue) {
                    if (((StackValue) rhs).getStackValue().equals(stackSSALabel)) {
                        possibleAliases.get(stackSSALabel).add(new LValueStatementContainer(assignment.getCreatedLValue(), statementContainer));
                    }
                }
            }
            usages.get(stackSSALabel).add(statementContainer);
            return null;
        }

        /* This could be a lot more functional - for now, just pick the first entry in the list -
         * If all the others, when used, can be seen to be at the same version as the first one.
         * (the first one which is NOT a stackSSALabel)
         */
        private LValue getAlias(StackSSALabel stackSSALabel) {
            List<LValueStatementContainer> possibleAliasList = possibleAliases.get(stackSSALabel);
            if (possibleAliasList.isEmpty()) return null;
            LValue guessAlias = null;
            StatementContainer guessStatement = null;
            for (LValueStatementContainer lValueStatementContainer : possibleAliasList) {
                if (!(lValueStatementContainer.lValue instanceof StackSSALabel)) {
                    guessAlias = lValueStatementContainer.lValue;
                    guessStatement = lValueStatementContainer.statementContainer;
                    break;
                }
            }
            if (guessAlias == null) return null;
            for (StatementContainer verifyStatement : usages.get(stackSSALabel)) {
                /*
                 * verify that 'guessAlias' is the same version in verifyStatement
                 * as it is in guessStatement.
                 */
                if (!guessStatement.getSSAIdentifiers().isValidReplacement(guessAlias, verifyStatement.getSSAIdentifiers())) {
                    return null;
                }
            }

            /*
             * ok, guessAlias is a valid replacement for stackSSALabel.
             */
            return guessAlias;
        }

        public void inferAliases() {
            for (Map.Entry<StackSSALabel, ExpressionStatement> multi : multiFound.entrySet()) {
                /*
                 * How many aliases does this have?
                 */
                StackSSALabel stackSSALabel = multi.getKey();
                LValue alias = getAlias(stackSSALabel);
                if (alias != null) {
                    /* The assignment between stackSSAlabel and alias can be elided, and
                     * referenced to stackSSALabel can be replaced with references to alias.
                     */
                    System.out.println("We can replace " + stackSSALabel + " with " + multi.getValue().expression);
                    found.put(stackSSALabel, multi.getValue());
                    System.out.println("And then subsequently " + alias);
                    aliasReplacements.put(stackSSALabel, new LValueExpression(alias));
                }
            }
        }

        @Override
        public boolean explicitlyReplaceThisLValue(LValue lValue) {
            return false;
        }
    }


    private static class LValueStatementContainer {
        private final LValue lValue;
        private final StatementContainer statementContainer;

        private LValueStatementContainer(LValue lValue, StatementContainer statementContainer) {
            this.lValue = lValue;
            this.statementContainer = statementContainer;
        }
    }
}
