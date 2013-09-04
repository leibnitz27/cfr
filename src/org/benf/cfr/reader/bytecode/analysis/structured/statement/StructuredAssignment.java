package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;


/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public class StructuredAssignment extends AbstractStructuredStatement implements BoxingProcessor {

    private LValue lvalue;
    private Expression rvalue;
    boolean isCreator;

    public StructuredAssignment(LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
        this.isCreator = false;
    }

    public StructuredAssignment(LValue lvalue, Expression rvalue, boolean isCreator) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
        this.isCreator = isCreator;
    }


    @Override
    public Dumper dump(Dumper dumper) {
        if (isCreator) {
            dumper.print(lvalue.getInferredJavaType().getJavaTypeInstance().toString() + " ");
        }
        dumper.dump(lvalue).print(" = ").dump(rvalue).endCodeln();
        return dumper;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        rvalue.collectUsedLValues(scopeDiscoverer);
        // todo - what if rvalue is an assignment?
        lvalue.collectLValueAssignments(rvalue, getContainer(), scopeDiscoverer);
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        if (!localVariable.equals(lvalue)) {
            throw new IllegalArgumentException("Being asked to mark creator for wrong variable");
        }
        isCreator = true;
        InferredJavaType inferredJavaType = localVariable.getInferredJavaType();
        if (inferredJavaType.isClash()) {
            inferredJavaType.collapseTypeClash();
        }
    }

    @Override
    public List<LocalVariable> findCreatedHere() {
        if (isCreator) {
            return ListFactory.newList((LocalVariable) lvalue);
        } else {
            return null;
        }
    }

    public LValue getLvalue() {
        return lvalue;
    }

    public Expression getRvalue() {
        return rvalue;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!this.equals(o)) return false;
        matchIterator.advance();
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof StructuredAssignment)) return false;
        StructuredAssignment other = (StructuredAssignment) o;
        if (!lvalue.equals(other.lvalue)) return false;
        if (!rvalue.equals(other.rvalue)) return false;
//        if (isCreator != other.isCreator) return false;
        return true;
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        expressionRewriter.handleStatement(getContainer());
        rvalue = expressionRewriter.rewriteExpression(rvalue, null, this.getContainer(), null);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        rvalue = boxingRewriter.sugarNonParameterBoxing(rvalue, lvalue.getInferredJavaType().getJavaTypeInstance());
        return true;
    }
}

