package org.benf.cfr.reader.bytecode.analysis.structured.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.ExpressionRewriterTransformer;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredDefinition;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.annotation.Nullable;
import org.benf.cfr.reader.util.output.Dumper;

public class StructuredCaseDefinitionExpression extends AbstractExpression {

    @Nullable
    private final StructuredDefinition content;
    @Nullable
    private final ConditionalExpression predicate;

    public StructuredCaseDefinitionExpression(InferredJavaType inferredJavaType, StructuredDefinition content, ConditionalExpression predicate) {
        super(BytecodeLoc.TODO, inferredJavaType);
        this.content = content;
        this.predicate = predicate;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.TODO;
    }

    /*
     * This is sub optimal - we shouldn't be shallow copying here, but I don't
     * want to add deepClone to the structuredStatement.
     */
    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (content != null) {
            content.collectTypeUsages(collector);
        }
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        StructuredScope scope = new StructuredScope();
        if (content != null) {
            scope.add(content);
            new ExpressionRewriterTransformer(expressionRewriter).transform(content, scope);
        }
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        // Fugly.  TODO: Fix interface.
        if (lValueUsageCollector instanceof LValueScopeDiscoverer) {
            LValueScopeDiscoverer scopeDiscoverer = (LValueScopeDiscoverer) lValueUsageCollector;
//            scopeDiscoverer.enterBlock(content);
            if (content != null) {
                content.traceLocalVariableScope(scopeDiscoverer);
            }
//            scopeDiscoverer.leaveBlock(content);
        }
    }


    @Override
    public Precedence getPrecedence() {
        return Precedence.WEAKEST;
    }

    @Nullable
    public StructuredStatement getContent() {
        return content;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (content != null) {
            LValue.Creation.dump(d, content.getLvalue());
            if (predicate != null) {
                d.separator(" ");
                d.keyword("when");
                d.separator(" ");
                predicate.dump(d);
            }
        }
        return d;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StructuredCaseDefinitionExpression that = (StructuredCaseDefinitionExpression) o;

        if ((content == null) != (that.content == null)) return false;
        if (content != null && !content.equals(that.content)) return false;
        if ((predicate == null) != (that.predicate == null)) return false;
        if (predicate != null && !predicate.equals(that.predicate)) return false;
        return true;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        StructuredCaseDefinitionExpression other = (StructuredCaseDefinitionExpression) o;
        if (!constraint.equivalent(content, other.content)) return false;
        return true;
    }

}
