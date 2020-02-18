package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractMutatingAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.ScopeDiscoverInfoCache;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class StructuredFor extends AbstractStructuredBlockStatement {
    private ConditionalExpression condition;
    private AssignmentSimple initial;
    private List<AbstractAssignmentExpression> assignments;
    private final BlockIdentifier block;
    private boolean isCreator;

    public StructuredFor(ConditionalExpression condition, AssignmentSimple initial, List<AbstractAssignmentExpression> assignments, Op04StructuredStatement body, BlockIdentifier block) {
        super(body);
        this.condition = condition;
        this.initial = initial;
        this.assignments = assignments;
        this.block = block;
        this.isCreator = false;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(condition);
        collector.collectFrom(assignments);
        super.collectTypeUsages(collector);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.label(block.getName(), true);
        dumper.keyword("for ").separator("(");
        if (initial != null) {
            if (isCreator) {
                // The reason this looks wrong is because initial should be a structured definition here....
                LValue.Creation.dump(dumper, initial.getCreatedLValue()).operator(" = ").dump(initial.getRValue()).separator(";");
            } else {
                dumper.dump(initial);
            }
            dumper.removePendingCarriageReturn();
        } else {
            dumper.separator(";");
        }
        dumper.print(" ").dump(condition).separator("; ");
        boolean first = true;
        for (Expression assignment : assignments) {
            first = StringUtils.comma(first, dumper);
            dumper.dump(assignment);
        }
        dumper.separator(") ");
        getBody().dump(dumper);
        return dumper;
    }

    @Override
    public boolean isScopeBlock() {
        return true;
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        getBody().linearizeStatementsInto(out);
    }

    @Override
    public BlockIdentifier getBreakableBlockOrNull() {
        return block;
    }

    @Override
    public boolean supportsBreak() {
        return true;
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        // While it's not strictly speaking 2 blocks, we can model it as the statement / definition
        // section of the for as being an enclosing block.  (otherwise we add the variable in the wrong scope).
        scopeDiscoverer.enterBlock(this);
        for (Expression assignment : assignments) {
            assignment.collectUsedLValues(scopeDiscoverer);
        }
        condition.collectUsedLValues(scopeDiscoverer);
        if (initial != null) {
            LValue lValue = initial.getCreatedLValue();
            Expression expression = initial.getRValue();
            Expression rhs = expression;
            LValue lv2 = lValue;
            do {
                scopeDiscoverer.collect(lv2);
                if (rhs instanceof AssignmentExpression) {
                    AssignmentExpression assignmentExpression = (AssignmentExpression) rhs;
                    lv2 = assignmentExpression.getlValue();
                    rhs = assignmentExpression.getrValue();
                } else {
                    lv2 = null;
                    rhs = null;
                }
            } while (lv2 != null);
            lValue.collectLValueAssignments(expression, this.getContainer(), scopeDiscoverer);
        }
        scopeDiscoverer.processOp04Statement(getBody());
        scopeDiscoverer.leaveBlock(this);
    }

    @Override
    public void markCreator(LValue scopedEntity, StatementContainer<StructuredStatement> hint) {
        LValue lValue = null;
        if (initial != null) lValue = initial.getCreatedLValue();
        if (!scopedEntity.equals(lValue)) {
            throw new IllegalStateException("Being asked to define something I can't define.");
        }
        this.isCreator = true;
    }

    @Override
    public boolean canDefine(LValue scopedEntity, ScopeDiscoverInfoCache factCache) {
        LValue lValue = null;
        if (initial != null) lValue = initial.getCreatedLValue();
        if (scopedEntity == null) return false;
        return scopedEntity.equals(lValue);
    }

    @Override
    public List<LValue> findCreatedHere() {
        if (!isCreator) return null;
        if (initial == null) return null;
        LValue created = initial.getCreatedLValue();
        if (!(created instanceof LocalVariable)) return null;

        return ListFactory.newImmutableList(created);
    }

    @Override
    public String suggestName(LocalVariable createdHere, Predicate<String> testNameUsedFn) {
        JavaTypeInstance loopType = createdHere.getInferredJavaType().getJavaTypeInstance();

        if (!(assignments.get(0) instanceof AbstractMutatingAssignmentExpression)) return null;

        if (!(loopType instanceof RawJavaType)) return null;
        RawJavaType rawJavaType = (RawJavaType) loopType;
        switch (rawJavaType) {
            case INT:
            case SHORT:
            case LONG:
                break;
            default:
                return null;
        }
        String[] poss = {"i", "j", "k"};
        for (String posss : poss) {
            if (!testNameUsedFn.test(posss)) {
                return posss;
            }
        }
        return "i"; // And we'll
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        condition = expressionRewriter.rewriteExpression(condition, null, this.getContainer(), null);
        initial.rewriteExpressions(expressionRewriter, null);
        for (int x = 0; x < assignments.size(); ++x) {
            assignments.set(x, (AbstractAssignmentExpression)expressionRewriter.rewriteExpression(assignments.get(x), null, this.getContainer(), null));
        }
    }

    public BlockIdentifier getBlock() {
        return block;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredFor)) return false;
        StructuredFor other = (StructuredFor) o;
        if (!initial.equals(other.initial)) return false;
        if (condition == null) {
            if (other.condition != null) return false;
        } else {
            if (!condition.equals(other.condition)) return false;
        }
        if (!assignments.equals(other.assignments)) return false;
        if (!block.equals(other.block)) return false;
        // Don't check locality.
        matchIterator.advance();
        return true;
    }

}
