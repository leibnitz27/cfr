package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.StringUtils;
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
        if (block.hasForeignReferences()) dumper.print(block.getName() + " : ");
        dumper.print("for (");
        if (initial != null) {
            // It's a big grotty to have creator here, but no worse that pushing it into Assignmentsimple
            if (isCreator) {
                dumper.dump(initial.getCreatedLValue().getInferredJavaType().getJavaTypeInstance()).print(" ");
            }
            dumper.dump(initial);
            dumper.removePendingCarriageReturn();
        } else {
            dumper.print(";");
        }
        dumper.print(" ").dump(condition).print("; ");
        boolean first = true;
        for (Expression assignment : assignments) {
            first = StringUtils.comma(first, dumper);
            dumper.dump(assignment);
        }
        dumper.print(") ");
        getBody().dump(dumper);
        return dumper;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        scope.add(this);
        try {
            getBody().transform(transformer, scope);
        } finally {
            scope.remove(this);
        }
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
            lValue.collectLValueAssignments(expression, this.getContainer(), scopeDiscoverer);
        }
        getBody().traceLocalVariableScope(scopeDiscoverer);
        scopeDiscoverer.leaveBlock(this);
    }

    @Override
    public void markCreator(LValue scopedEntity) {
        LValue lValue = null;
        if (initial != null) lValue = initial.getCreatedLValue();
        if (!scopedEntity.equals(lValue)) {
            throw new IllegalStateException("Being asked to define something I can't define.");
        }
        this.isCreator = true;
    }

    @Override
    public boolean canDefine(LValue scopedEntity) {
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

        return ListFactory.newList(created);
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
