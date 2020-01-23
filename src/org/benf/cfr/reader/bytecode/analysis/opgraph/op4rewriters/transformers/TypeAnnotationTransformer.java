package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.types.TypeAnnotationHelper;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.AttributeTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import static org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryValue.*;

public class TypeAnnotationTransformer implements StructuredStatementTransformer, ExpressionRewriter {

    private List<AnnotationTableTypeEntry> variableAnnotations;
    private List<AnnotationTableTypeEntry> catchAnnotations;

    private final SortedMap<Integer, Integer> instrsByOffset;
    private final DecompilerComments comments;

    public TypeAnnotationTransformer(AttributeTypeAnnotations vis, AttributeTypeAnnotations invis, SortedMap<Integer, Integer> instrsByOffset, DecompilerComments comments) {
        this.instrsByOffset = instrsByOffset;
        this.comments = comments;
        this.variableAnnotations = ListFactory.combinedOptimistic(
                vis == null ? null : vis.getAnnotationsFor(type_localvar, type_resourcevar),
                invis == null ? null : invis.getAnnotationsFor(type_localvar, type_resourcevar));

        this.catchAnnotations = ListFactory.combinedOptimistic(
                vis == null ? null : vis.getAnnotationsFor(type_throws),
                invis == null ? null : invis.getAnnotationsFor(type_throws));

    }

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        in.rewriteExpressions(this);
        return in;
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    // TODO : this is a scan PER USE.  It's woefully expensive. (Though why on earth would code be liberally scattered with
    // annotations?)
    private List<AnnotationTableTypeEntry> getLocalVariableAnnotations(final int offset, final int slot, final int tolerance) {
        // CFR may hold the offset the variable was /created/ at, which is 1 before it becomes valid.
        List<AnnotationTableTypeEntry> entries = variableAnnotations;
        if (entries.isEmpty()) return Collections.emptyList();

        entries = Functional.filter(entries, new Predicate<AnnotationTableTypeEntry>() {
            @Override
            public boolean test(AnnotationTableTypeEntry in) {
                TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget tgt = (TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget)in.getTargetInfo();
                return tgt.matches(offset, slot, tolerance);
            }
        });
        return entries;
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        Object rawStatement = statementContainer.getStatement();
        if (!(rawStatement instanceof StructuredStatement)) return;
        StructuredStatement stm = (StructuredStatement)rawStatement;

        if (stm instanceof StructuredCatch) {
            handleCatchStatement((StructuredCatch)stm);
            return;
        }

        if (variableAnnotations == null) return;
        /*
         * get anything created here.
         */
        List<LValue> createdHere = stm.findCreatedHere();
        if (createdHere == null || createdHere.isEmpty()) return;

        for (LValue lValue : createdHere) {
            if (lValue instanceof LocalVariable) {
                LocalVariable localVariable = (LocalVariable)lValue;
                int offset = localVariable.getOriginalRawOffset();
                int slot = localVariable.getIdx();
                if (offset < 0 || slot < 0) continue;

                // We allow instruction BEFORE offset.
                SortedMap<Integer, Integer> heapMap = instrsByOffset.headMap(offset);
                int offsetTolerance = heapMap.isEmpty() ? 1 : offset - heapMap.lastKey();

                List<AnnotationTableTypeEntry> entries = getLocalVariableAnnotations(offset, slot, offsetTolerance);
                if (entries == null || entries.isEmpty()) continue;

                JavaAnnotatedTypeInstance annotatedTypeInstance = localVariable.getAnnotatedCreationType();
                if (annotatedTypeInstance == null) {
                    annotatedTypeInstance = localVariable.getInferredJavaType().getJavaTypeInstance().getAnnotatedInstance();
                    localVariable.setCustomCreationType(annotatedTypeInstance);
                }

                TypeAnnotationHelper.apply(annotatedTypeInstance, entries, comments);
            }
        }
    }

    private void handleCatchStatement(StructuredCatch stm) {
        // Need to link our catch back to the ORIGINAL catch index.
        // TODO : NYI - we need to link up.
    }

}
