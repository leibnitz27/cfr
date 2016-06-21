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
import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.entities.attributes.TypePathPart;
import org.benf.cfr.reader.util.DecompilerComments;

import java.util.List;
import java.util.SortedMap;

public class TypeAnnotationTransformer implements StructuredStatementTransformer, ExpressionRewriter {

    private final AttributeRuntimeVisibleTypeAnnotations typeAnnotations;
    private final SortedMap<Integer, Integer> instrsByOffset;
    private final DecompilerComments comments;

    public TypeAnnotationTransformer(AttributeRuntimeVisibleTypeAnnotations typeAnnotations, SortedMap<Integer, Integer> instrsByOffset, DecompilerComments comments) {
        this.typeAnnotations = typeAnnotations;
        this.instrsByOffset = instrsByOffset;
        this.comments = comments;
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

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        Object rawStatement = statementContainer.getStatement();
        if (!(rawStatement instanceof StructuredStatement)) return;
        StructuredStatement stm = (StructuredStatement)rawStatement;

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

                List<AnnotationTableTypeEntry<TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget>> entries = typeAnnotations.getLocalVariableAnnotations(offset, slot, offsetTolerance);

                if (entries.isEmpty()) continue;

                JavaAnnotatedTypeInstance annotatedTypeInstance = localVariable.getAnnotatedCreationType();
                if (annotatedTypeInstance == null) {
                    annotatedTypeInstance = localVariable.getInferredJavaType().getJavaTypeInstance().getAnnotatedInstance();
                    localVariable.setCustomCreationType(annotatedTypeInstance);
                }

                for (AnnotationTableTypeEntry<TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget> entry : entries) {
                    JavaAnnotatedTypeIterator iterator = annotatedTypeInstance.pathIterator();
                    for (TypePathPart part : entry.getTypePath().segments) {
                        iterator = part.apply(iterator, comments);
                    }
                    iterator.apply(entry);
                }
            }
        }
    }

}
