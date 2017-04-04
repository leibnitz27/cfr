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
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredBreak;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredContinue;
import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.entities.attributes.TypePathPart;
import org.benf.cfr.reader.util.DecompilerComments;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;

public class ControlFlowCleaningTransformer implements StructuredStatementTransformer, ExpressionRewriter {

    public ControlFlowCleaningTransformer() {
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        // If we're inside a while loop, then a (direct) continue at the 'notionally last' item has no value.
        StructuredStatement stm = (StructuredStatement)statementContainer.getStatement();
    }

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {

        if (scope.get(0) instanceof Block) {
            if (in instanceof StructuredBreak) {
                StructuredBreak inb = (StructuredBreak) in;
                Set<Op04StructuredStatement> falls = scope.getNextFallThrough(in);
                /*
                 * If one of these is also a break, AND all of these have the same target as this, this is redundant.
                 * (we check that all of them are in the scope).
                 */
                for (Op04StructuredStatement fall : falls) {
                    StructuredStatement stm = fall.getStatement();
                    if (stm instanceof StructuredBreak) {
                        StructuredBreak stmb = (StructuredBreak) stm;
                        if (stmb.getBreakBlock() == inb.getBreakBlock()) {
                            return new StructuredComment("");
                        }
                    }
                }
                return in;
            }
            if (in instanceof StructuredContinue) {
                StructuredContinue cont = (StructuredContinue) in;

                Set<Op04StructuredStatement> falls = scope.getNextFallThrough(in);
                for (Op04StructuredStatement fall : falls) {
                    StructuredStatement stm = fall.getStatement();
                    if (stm instanceof StructuredContinue) {
                        StructuredContinue stmb = (StructuredContinue) stm;
                        if (stmb.getContinueTgt() == cont.getContinueTgt()) {
                            return new StructuredComment("");
                        }
                    }
                    if (stm instanceof StructuredComment) {
                        continue;
                    }
                    return in;
                }

                BlockIdentifier block = scope.getContinueBlock();
                if (block == cont.getContinueTgt()) {
                    return new StructuredComment("");
                }

                return in;
            }
        }

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

}
