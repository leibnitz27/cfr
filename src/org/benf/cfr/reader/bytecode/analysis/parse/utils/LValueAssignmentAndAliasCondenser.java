package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class LValueAssignmentAndAliasCondenser implements LValueRewriter<Statement>, LValueAssignmentCollector<Statement> {

    private static final Logger logger = LoggerFactory.create(LValueAssignmentAndAliasCondenser.class);

    //
    // Found states that key can be replaced with value.
    //
    private final Map<StackSSALabel, ExpressionStatement> found = MapFactory.newOrderedMap();
    private final Set<StackSSALabel> blacklisted = SetFactory.newOrderedSet();
    //
    // A chain of dup, copy assign can be considered to be an alias set.
    // we can replace references to subsequent temporaries with references to the first LValue.
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

    //
    // When we're EXPLICITLY being told that this NON SSA value can be moved to later in the
    // code (i.e.  ++x;  if (x) -> if (++x) )
    //
    private final Map<VersionedLValue, ExpressionStatement> mutableFound = MapFactory.newMap();


    @Override
    public void collect(StackSSALabel lValue, StatementContainer<Statement> statementContainer, Expression value) {
        found.put(lValue, new ExpressionStatement(value, statementContainer));
    }

    @Override
    public void collectMultiUse(StackSSALabel lValue, StatementContainer<Statement> statementContainer, Expression value) {
        multiFound.put(lValue, new ExpressionStatement(value, statementContainer));
    }

    @Override
    public void collectMutatedLValue(LValue lValue, StatementContainer<Statement> statementContainer, Expression value) {
        SSAIdent version = statementContainer.getSSAIdentifiers().getSSAIdentOnExit(lValue);
        if (null != mutableFound.put(new VersionedLValue(lValue, version), new ExpressionStatement(value, statementContainer))) {
            throw new ConfusedCFRException("Duplicate versioned SSA Ident.");
        }
    }

    // We're not interested in local variable assignments here.
    @Override
    public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<Statement> statementContainer, Expression value) {
    }

    Map<Expression, Expression> cache = MapFactory.newMap();

    Set<LValue> findAssignees(Statement s) {
        if (!(s instanceof AssignmentSimple)) return null;
        AssignmentSimple assignmentSimple = (AssignmentSimple) s;
        Set<LValue> res = SetFactory.newSet();
        res.add(assignmentSimple.getCreatedLValue());
        Expression rvalue = assignmentSimple.getRValue();
        while (rvalue instanceof AssignmentExpression) {
            AssignmentExpression assignmentExpression = (AssignmentExpression) rvalue;
            res.add(assignmentExpression.getlValue());
            rvalue = assignmentExpression.getrValue();
        }
        return res;
    }

    @Override
    public LValueRewriter getWithFixed(Set fixed) {
        return this;
    }

    public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<Statement> lvSc) {
        if (!(lValue instanceof StackSSALabel)) return null;

        StackSSALabel stackSSALabel = (StackSSALabel) lValue;

        if (!found.containsKey(stackSSALabel)) return null;
        if (blacklisted.contains(stackSSALabel)) {
            return null;
        }
        ExpressionStatement pair = found.get(stackSSALabel);
        // res is a valid replacement for lValue in an rValue, IF no mutable fields have different version
        // identifiers (SSA tags)
        StatementContainer<Statement> statementContainer = pair.statementContainer;
        SSAIdentifiers replacementIdentifiers = statementContainer == null ? null : statementContainer.getSSAIdentifiers();
        // We're saying we can replace lValue with res.
        // This is only valid if res has a single possible value in ssaIdentifiers, and it's the same as in replacementIdentifiers.
        Expression res = pair.expression;

        if (replacementIdentifiers != null) {
            LValueUsageCollectorSimple lvcInSource = new LValueUsageCollectorSimple();
            res.collectUsedLValues(lvcInSource);

            for (LValue resLValue : lvcInSource.getUsedLValues()) {
                replaceTest:
                if (!ssaIdentifiers.isValidReplacement(resLValue, replacementIdentifiers)) {
                    /* Second chance - self assignment in the source.
                    */
                    Set<LValue> assignees = findAssignees(lvSc.getStatement());
                    if (assignees != null) {
                        if (assignees.contains(resLValue)) {
                            Op03SimpleStatement lv03 = (Op03SimpleStatement) lvSc;
                            for (Op03SimpleStatement source : lv03.getSources()) {
                                if (!source.getSSAIdentifiers().isValidReplacementOnExit(resLValue, replacementIdentifiers)) {
                                    return null;
                                }
                            }
                        /*
                         * Ok, we can get away with it.
                         */
                            break replaceTest;
                        }
                    }
                    return null;
                }
            }

            /*
             * If the source statement changes anything (i.e. replacement identifiers before != after)
             * If the source has a direct child other than the target, check that the other targets don't require the
             * changed value at its new level.
             * (otherwise we might move a ++ past a subsequent usage).
             */
            Set<LValue> changes = (statementContainer instanceof Op03SimpleStatement) ? replacementIdentifiers.getChanges() : null;
            if (changes != null && !changes.isEmpty()) {
                Op03SimpleStatement container = (Op03SimpleStatement)statementContainer;
                for (Op03SimpleStatement target : container.getTargets()) {
                    if (target != lvSc) {
                        for (LValue change : changes) {
                            if (target.getSSAIdentifiers().getSSAIdentOnEntry(change).equals(replacementIdentifiers.getSSAIdentOnExit(change))) {
                                // We can't move this statement (yet).
                                return null;
                            }
                        }
                    }
                }
            }

            /*
             * We've decided we're going to make the substitution - now changes need to be applied to the target identifiers.
             */
            if (changes != null && !changes.isEmpty()) {
                SSAIdentifiers tgtIdents = lvSc.getSSAIdentifiers();
                for (LValue change : changes) {
                    // The change is now being applied inside lvsc.
                    tgtIdents.setKnownIdentifierOnEntry(change, replacementIdentifiers.getSSAIdentOnEntry(change));
                }
            }
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


        Expression prev = null;
        if (res instanceof StackValue && ((StackValue) res).getStackValue() == stackSSALabel) {
            prev = res;
        }
        // res not null on entry, prev guaranteed to be initialised.
        while (res != null && res != prev) {
            prev = res;
            if (cache.containsKey(res)) {
                res = cache.get(res);
                prev = res;
            }
            res = res.replaceSingleUsageLValues(this, ssaIdentifiers, lvSc);
        }

        cache.put(new StackValue(stackSSALabel), prev);

        return prev;
    }

    @Override
    public boolean explicitlyReplaceThisLValue(LValue lValue) {
        return false;
    }

    /*
     * This is a bit of a hack.  We need to avoid the (VANISHINGLY RARE)
     * circumstance where we have
     *
     * s0 = THING
     * s1 = s0
     * s2 = s1
     * s3 = s1
     * v1 = s2
     * v2 = s3
     *
     * These all look like they're replaceable, (and we need to allow the split because of inline array
     * resugaring.)  However, we'll end up with a possible duplicate call of THING.  We could fix this with a single pass
     * which nops out all 1->1 assignments, but for now I'm checking this way, seems less work.).
     */
    @Override
    public void checkPostConditions(LValue lValue, Expression rValue) {
        if (!(lValue instanceof StackSSALabel)) return;
        StackSSALabel label = (StackSSALabel)lValue;
        if (aliasReplacements.containsKey(label)) return;
        if (!(found.containsKey(label))) return;
        long count = label.getStackEntry().getUsageCount();
        if (count > 1 && !rValue.isSimple()) {
            blacklisted.add(label);
        }
    }

    private static class ExpressionStatement {
        private final Expression expression;
        private final StatementContainer<Statement> statementContainer;

        private ExpressionStatement(Expression expression, StatementContainer<Statement> statementContainer) {
            this.expression = expression;
            this.statementContainer = statementContainer;
        }
    }

    public AliasRewriter getAliasRewriter() {
        return new AliasRewriter();
    }

    public class AliasRewriter implements LValueRewriter<Statement> {
        private final Map<StackSSALabel, List<StatementContainer<Statement>>> usages = MapFactory.newLazyMap(
                new UnaryFunction<StackSSALabel, List<StatementContainer<Statement>>>() {
                    @Override
                    public List<StatementContainer<Statement>> invoke(StackSSALabel ignore) {
                        return ListFactory.newList();
                    }
                }
        );
        private final Map<StackSSALabel, List<LValueStatementContainer>> possibleAliases = MapFactory.newLazyMap(
                new UnaryFunction<StackSSALabel, List<LValueStatementContainer>>() {
                    @Override
                    public List<LValueStatementContainer> invoke(StackSSALabel ignore) {
                        return ListFactory.newList();
                    }
                }
        );

        @Override
        public LValueRewriter getWithFixed(Set fixed) {
            return this;
        }

        @Override
        public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<Statement> statementContainer) {
            if (!(lValue instanceof StackSSALabel)) return null;
            StackSSALabel stackSSALabel = (StackSSALabel) lValue;

            if (!multiFound.containsKey(lValue)) return null;
            /* If it's an assignment, then put it in the 'possible alias'
             * list.
             */
            if (statementContainer.getStatement() instanceof AssignmentSimple) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) (statementContainer.getStatement());
                Expression rhs = assignmentSimple.getRValue();
                if (rhs instanceof StackValue) {
                    if (((StackValue) rhs).getStackValue().equals(stackSSALabel)) {
                        possibleAliases.get(stackSSALabel).add(new LValueStatementContainer(assignmentSimple.getCreatedLValue(), statementContainer));
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
        private LValue getAlias(StackSSALabel stackSSALabel, ExpressionStatement target) {
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
            // This isn't right.  We should allow
            //
            // x[1] = 3
            // a = x[1]
            // However, since we're looking at this from the point of view of SSALabels, we don't have that info here
            // so we ban LValues like this, to stop array creation being reordered.
            final LValue returnGuessAlias = guessAlias;
            List<LValue> checkThese = ListFactory.newList();
            if (guessAlias instanceof ArrayVariable) {
                ArrayVariable arrayVariable = (ArrayVariable) guessAlias;
                ArrayIndex arrayIndex = arrayVariable.getArrayIndex();
                Expression array = arrayIndex.getArray();
                if (!(array instanceof LValueExpression)) return null;
                LValueExpression lValueArrayIndex = (LValueExpression) array;
                checkThese.add(lValueArrayIndex.getLValue());
                Expression index = arrayIndex.getIndex();
                if (index instanceof LValueExpression) {
                    checkThese.add(((LValueExpression) index).getLValue());
                } else if (index instanceof Literal) {
                } else {
                    return null;
                }
            } else {
                checkThese.add(guessAlias);
            }
            for (StatementContainer<Statement> verifyStatement : usages.get(stackSSALabel)) {
                /*
                 * verify that 'guessAlias' is the same version in verifyStatement
                 * as it is in guessStatement.
                 */
                if (verifyStatement.getStatement().doesBlackListLValueReplacement(stackSSALabel, target.expression)) return null;
                for (LValue checkThis : checkThese) {
                    if (guessStatement == verifyStatement) continue;
                    if (!verifyStatement.getSSAIdentifiers().isValidReplacement(checkThis, guessStatement.getSSAIdentifiers())) {
                        return null;
                    }
                }
            }

            /*
             * ok, guessAlias is a valid replacement for stackSSALabel.
             */
            return returnGuessAlias;
        }

        public void inferAliases() {
            for (Map.Entry<StackSSALabel, ExpressionStatement> multi : multiFound.entrySet()) {
                /*
                 * How many aliases does this have?
                 */
                StackSSALabel stackSSALabel = multi.getKey();
                LValue alias = getAlias(stackSSALabel, multi.getValue());
                if (alias != null) {
                    /* The assignment between stackSSAlabel and alias can be elided, and
                     * referenced to stackSSALabel can be replaced with references to alias.
                     */
                    found.put(stackSSALabel, multi.getValue());
                    aliasReplacements.put(stackSSALabel, new LValueExpression(alias));
                }
            }
        }

        @Override
        public boolean explicitlyReplaceThisLValue(LValue lValue) {
            return false;
        }

        @Override
        public void checkPostConditions(LValue lValue, Expression rValue) {
        }
    }


    public MutationRewriterFirstPass getMutationRewriterFirstPass() {
        if (mutableFound.isEmpty()) return null;
        return new MutationRewriterFirstPass();
    }


    public class MutationRewriterFirstPass implements LValueRewriter<Statement> {

        private final Map<VersionedLValue, Set<StatementContainer>> mutableUseFound = MapFactory.newLazyMap(new UnaryFunction<VersionedLValue, Set<StatementContainer>>() {
            @Override
            public Set<StatementContainer> invoke(VersionedLValue arg) {
                return SetFactory.newSet();
            }
        });

        /* Bit cheeky, we'll never actually replace here, but use this pass to collect info. */
        @Override
        public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<Statement> statementContainer) {
            SSAIdent ssaIdent = ssaIdentifiers.getSSAIdentOnExit(lValue);
            if (ssaIdent != null) {
                VersionedLValue versionedLValue = new VersionedLValue(lValue, ssaIdent);
                if (mutableFound.containsKey(versionedLValue)) {
                    // Note a use of this @ statementContainer.
                    mutableUseFound.get(versionedLValue).add(statementContainer);
                }
            }
            return null;
        }

        @Override
        public LValueRewriter getWithFixed(Set fixed) {
            return this;
        }

        @Override
        public boolean explicitlyReplaceThisLValue(LValue lValue) {
            return true;
        }

        @Override
        public void checkPostConditions(LValue lValue, Expression rValue) {

        }

        /* Given an original statement (in which we're pre-incrementing x), and a number of uses of X at the value
                 * 'after' the pre-increment, we want to determine if there is a single use which dominates all others.
                 *
                 * We can accomplish this with a DFS starting at the start, which aborts at each node, but if it sees 2, then
                 * game over.
                 *
                 * We can further simplify - if we see a node with 2 targets, we can abort.
                 *
                 * todo : StatementContainer doesn't have children.
                 */
        private StatementContainer getUniqueParent(StatementContainer start, final Set<StatementContainer> seen) {
            Op03SimpleStatement o3current = (Op03SimpleStatement) start;

            while (true) {
                if (seen.contains(o3current)) {
                    return o3current;
                }
                List<Op03SimpleStatement> targets = o3current.getTargets();
                if (targets.size() != 1) return null;
                o3current = targets.get(0);
                if (o3current == start) {
                    return null;
                }
            }
        }

        public MutationRewriterSecondPass getSecondPassRewriter() {
            /* Now, for Every entry in mutableUseFound, we will get a set of statements.
             * We want to make sure that ONE of these statements is the 'ultimate parent'.
             * (i.e. there is one which is always hit first when traversing the targets of the original
             * declaration statement).
             */
            Map<VersionedLValue, StatementContainer> replacableUses = MapFactory.newMap();
            for (Map.Entry<VersionedLValue, Set<StatementContainer>> entry : mutableUseFound.entrySet()) {
                ExpressionStatement definition = mutableFound.get(entry.getKey());
                StatementContainer uniqueParent = getUniqueParent(definition.statementContainer, entry.getValue());
                if (uniqueParent != null) {
                    replacableUses.put(entry.getKey(), uniqueParent);
                }
            }

            if (replacableUses.isEmpty()) return null;

            return new MutationRewriterSecondPass(replacableUses);
        }
    }

    private static final Set<SSAIdent> emptyFixed = SetFactory.newSet();

    public class MutationRewriterSecondPass implements LValueRewriter<Statement> {
        private final Set<SSAIdent> fixed;
        private final Map<VersionedLValue, StatementContainer> mutableReplacable;

        private MutationRewriterSecondPass(Map<VersionedLValue, StatementContainer> mutableReplacable) {
            this.mutableReplacable = mutableReplacable;
            this.fixed = emptyFixed;
        }

        private MutationRewriterSecondPass(Map<VersionedLValue, StatementContainer> mutableReplacable, Set<SSAIdent> fixed) {
            this.mutableReplacable = mutableReplacable;
            this.fixed = fixed;
        }

        @Override
        public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<Statement> statementContainer) {
            SSAIdent ssaIdent = ssaIdentifiers.getSSAIdentOnExit(lValue);
            if (ssaIdent != null) {
                VersionedLValue versionedLValue = new VersionedLValue(lValue, ssaIdent);
                StatementContainer canReplaceIn = mutableReplacable.get(versionedLValue);
                if (canReplaceIn == statementContainer) {
                    ExpressionStatement replaceWith = mutableFound.get(versionedLValue);
                    StatementContainer<Statement> replacement = replaceWith.statementContainer;
                    if (replacement == statementContainer) return null;

                    SSAIdentifiers previousIdents = replacement.getSSAIdentifiers();
                    Set fixedPrevious = previousIdents.getFixedHere();
                    if (SetUtil.hasIntersection(this.fixed, fixedPrevious)) {
                        return null;
                    }

                    // Only the first time.
                    mutableReplacable.remove(versionedLValue);
                    replacement.nopOut();
                    SSAIdentifiers currentIdents = statementContainer.getSSAIdentifiers();
                    currentIdents.setKnownIdentifierOnEntry(lValue, previousIdents.getSSAIdentOnEntry(lValue));
                    currentIdents.fixHere(previousIdents.getFixedHere());
                    return replaceWith.expression;
                }
            }
            return null;
        }

        @Override
        public LValueRewriter getWithFixed(Set fixed) {
            return new MutationRewriterSecondPass(this.mutableReplacable, SetFactory.newSet(this.fixed, fixed));
        }

        @Override
        public boolean explicitlyReplaceThisLValue(LValue lValue) {
            return true;
        }

        @Override
        public void checkPostConditions(LValue lValue, Expression rValue) {

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

    private final static class VersionedLValue {
        private final LValue lValue;
        private final SSAIdent ssaIdent;

        private VersionedLValue(LValue lValue, SSAIdent ssaIdent) {
            this.lValue = lValue;
            this.ssaIdent = ssaIdent;
        }

        @Override
        public int hashCode() {
            return lValue.hashCode() + 31 * ssaIdent.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof VersionedLValue)) return false;

            VersionedLValue other = (VersionedLValue) o;
            return lValue.equals(other.lValue) &&
                    ssaIdent.equals(other.ssaIdent);
        }
    }
}
