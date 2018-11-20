package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * A first pass of lvalue collapsing.
 *
 * We are VERY pessimistic about what we accept.  We will only collapse a value if it's been assigned in the previous
 * statement, and never subsequently accessed.
 *
 * Initial pass scans for values that are only ever read once + written once.  We then walk BACKWARDS, collapsing these, and removing
 * previous assignment.
 */
public class LValuePropSimple {

    private static class AssignmentCollector implements LValueAssignmentCollector<Statement> {
        Map<StackSSALabel, StatementContainer<Statement>> assignments = MapFactory.newMap();
        Map<StackSSALabel, Expression> values = MapFactory.newMap();

        public void collect(StackSSALabel lValue, StatementContainer<Statement> statementContainer, Expression value) {
            if (assignments.containsKey(lValue)) {
                assignments.put(lValue, null);
                values.remove(lValue);
            } else {
                assignments.put(lValue, statementContainer);
                values.put(lValue, value);
            }
        }

        @Override
        public void collectMultiUse(StackSSALabel lValue, StatementContainer<Statement> statementContainer, Expression value) {
            assignments.put(lValue, null);
        }

        @Override
        public void collectMutatedLValue(LValue lValue, StatementContainer<Statement> statementContainer, Expression value) {
            if (lValue instanceof StackSSALabel) {
                assignments.put((StackSSALabel)lValue, null);
            }
        }

        @Override
        public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<Statement> statementContainer, Expression value) {
        }
    }

    private static class UsageCollector implements LValueUsageCollector {

        Map<StackSSALabel, Boolean> singleUsages = MapFactory.newMap();

        @Override
        public void collect(LValue lValue) {
            if (!(lValue instanceof StackSSALabel)) return;
            StackSSALabel stackSSALabel = (StackSSALabel)lValue;
            if (singleUsages.containsKey(stackSSALabel)) {
                singleUsages.put(stackSSALabel, null);
            } else {
                singleUsages.put(stackSSALabel, Boolean.TRUE);
            }
        }

        List<StackSSALabel> getSingleUsages() {
            List<StackSSALabel> res = ListFactory.newList();
            for (Map.Entry<StackSSALabel, Boolean> entry : singleUsages.entrySet()) {
                if (entry.getValue() == Boolean.TRUE) res.add(entry.getKey());
            }
            return res;
        }
    }

    public static List<Op03SimpleStatement> condenseSimpleLValues(List<Op03SimpleStatement> statementList) {
        AssignmentCollector assignmentCollector = new AssignmentCollector();
        UsageCollector usageCollector = new UsageCollector();

        for (Op03SimpleStatement statement : statementList) {
            statement.getStatement().collectLValueAssignments(assignmentCollector);
            statement.getStatement().collectLValueUsage(usageCollector);
        }

        Map<StackSSALabel, StatementContainer<Statement>> created = assignmentCollector.assignments;
        List<StackSSALabel> singleUsages = usageCollector.getSingleUsages();
        Map<StackSSALabel, Op03SimpleStatement> createdAndUsed = MapFactory.newMap();
        Map<Op03SimpleStatement, StackSSALabel> creations = MapFactory.newIdentityMap();
        for (StackSSALabel single : singleUsages) {
            StatementContainer<Statement> creation = created.get(single);
            if (creation != null) {
                createdAndUsed.put(single, (Op03SimpleStatement)creation);
                creations.put((Op03SimpleStatement)creation, single);
            }
        }

        int nopCount = 0;
        for (int x=statementList.size();x>1;--x) {
            Op03SimpleStatement prev = statementList.get(x-1);
            if (creations.containsKey(statementList.get(x-1))) {
                Op03SimpleStatement now = statementList.get(x);
                if (!(prev.getTargets().size() == 1 && prev.getTargets().get(0) == now)) continue;
                if (now.getSources().size() != 1) continue;
                UsageCollector oneUsagecollector = new UsageCollector();
                now.getStatement().collectLValueUsage(oneUsagecollector);
                /*
                 * Block identifier checks allow us to verify that we're not walking through any exceptions.
                 */
                if (!prev.getBlockIdentifiers().isEmpty()) continue;
                if (!now.getBlockIdentifiers().isEmpty()) continue;
                final StackSSALabel prevCreated = creations.get(prev);
                if (oneUsagecollector.getSingleUsages().contains(prevCreated)) {
                    final Expression rhs = assignmentCollector.values.get(prevCreated);

                    LValueRewriter<Statement> rewriter = new LValueRewriter<Statement>() {
                        @Override
                        public Expression getLValueReplacement(LValue lValue, SSAIdentifiers<LValue> ssaIdentifiers, StatementContainer<Statement> statementContainer) {
                            if (lValue.equals(prevCreated)) return rhs;
                            return null;
                        }

                        @Override
                        public boolean explicitlyReplaceThisLValue(LValue lValue) {
                            return lValue.equals(prevCreated);
                        }

                        @Override
                        public void checkPostConditions(LValue lValue, Expression rValue) {

                        }

                        @Override
                        public boolean needLR() {
                            return false;
                        }

                        @Override
                        public LValueRewriter getWithFixed(Set fixed) {
                            return this;
                        }
                    };

                    Statement nowS = now.getStatement();
                    nowS.replaceSingleUsageLValues(rewriter, null);
                    prev.replaceStatement(nowS);
                    /*
                     * Nop out this statement, but do so very aggressively.
                     */
                    prev.getTargets().clear(); // was only pointing at THIS.
                    now.getSources().clear();
                    for (Op03SimpleStatement target : now.getTargets()) {
                        target.replaceSource(now, prev);
                        prev.addTarget(target);
                    }
                    now.getTargets().clear();
                    now.nopOut();
                    nopCount++;
                }
            }
        }
        if (nopCount > 0) {
            statementList = Cleaner.removeUnreachableCode(statementList, false);
        }
        return statementList;
    }
}
