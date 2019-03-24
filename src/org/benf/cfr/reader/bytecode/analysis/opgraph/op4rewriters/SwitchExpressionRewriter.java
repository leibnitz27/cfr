package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SwitchExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CommentStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;

public class SwitchExpressionRewriter extends AbstractExpressionRewriter implements Op04Rewriter {

    // TODO : This is a very common pattern - linearize is treated as a util - we should just walk.
    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        for (int x=0;x<structuredStatements.size()-1;++x) {
            StructuredStatement s = structuredStatements.get(x);
            if (s instanceof StructuredDefinition) {
                replaceSwitch(structuredStatements, x);
            }
        }
    }

    private void replaceSwitch(List<StructuredStatement> structuredStatements, int x) {
        StructuredDefinition def = (StructuredDefinition)structuredStatements.get(x);
        StructuredStatement swat = structuredStatements.get(x+1);
        if (!(swat instanceof StructuredSwitch)) {
            return;
        }
        StructuredSwitch swatch = (StructuredSwitch)swat;
        LValue target = def.getLvalue();
        // At this point, the switch needs total coverage, and every item needs to assign
        // a single thing to target, or throw an exception;
        Op04StructuredStatement swBody = swatch.getBody();
        if (!(swBody.getStatement() instanceof Block)) {
            return;
        }
        Block b = (Block)swBody.getStatement();
        List<Op04StructuredStatement> content = b.getBlockStatements();
        int size = content.size();
        List<Pair<StructuredCase, Expression>> extracted = ListFactory.newList();
        List<Pair<Op04StructuredStatement, StructuredStatement>> replacements = ListFactory.newList();
        for (int itm = 0; itm < size; ++itm) {
            Pair<StructuredCase, Expression> e = extractSwitchEntryPair(target, content.get(itm), replacements,itm == size -1);
            if (e == null) {
                return;
            }
            extracted.add(e);
        }
        // Now we're sure we're doing the transformation....
        for (Pair<Op04StructuredStatement, StructuredStatement> replacement : replacements) {
            replacement.getFirst().replaceContainedStatement(replacement.getSecond());
        }
        List<SwitchExpression.Branch> items = ListFactory.newList();
        for (Pair<StructuredCase, Expression> e : extracted) {
            items.add(new SwitchExpression.Branch(e.getFirst().getValues(), e.getSecond()));
        }
        swat.getContainer().nopOut();
        StructuredAssignment switchStatement =
                new StructuredAssignment(target, new SwitchExpression(target.getInferredJavaType(), swatch.getSwitchOn(), items));
        def.getContainer().replaceStatement(switchStatement);
        switchStatement.markCreator(target, switchStatement.getContainer());
    }

    private final static Predicate<Op04StructuredStatement> notEmpty = new Predicate<Op04StructuredStatement>() {
        @Override
        public boolean test(Op04StructuredStatement in) {
            return !(in.getStatement() instanceof Nop || in.getStatement() instanceof CommentStatement);
        }
    };

    private Pair<StructuredCase, Expression> extractSwitchEntryPair(LValue target, Op04StructuredStatement item, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements, boolean last) {
        StructuredStatement stm = item.getStatement();
        if (!(stm instanceof StructuredCase)) {
            return null;
        }
        StructuredCase sc = (StructuredCase)stm;
        Expression res = extractSwitchEntry(target, sc.getBody(), replacements, last);
        if (res == null) {
            return null;
        }
        return Pair.make(sc, res);
    }

    /*
     * The body of a switch expression is a legitimate result if it assigns to the target or throws before every
     * exit point.
     *
     * All exit points must target the eventual target of the switch. (otherwise we could assign, then break
     * an outer block).
     * (fortunately by this time we're structured, so all exit points must be a structured break, or roll off the end.
     * a break inside an inner breakable construct is therefore not adequate).
     *
     * No assignment to the target can happen other than just prior to an exit point.
     */
    private Expression extractSwitchEntry(LValue target, Op04StructuredStatement body, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements, boolean last) {
        if (body.getStatement() instanceof Block) {
            Block block = (Block) body.getStatement();
            List<Op04StructuredStatement> blockStm = block.getBlockStatements();
            blockStm = Functional.filterOptimistic(blockStm, notEmpty);
            if (blockStm.size() == 2) {
                return extractOneSwitchAssignment(target, blockStm);
            } if (blockStm.size() == 1) {
                return extractOneSwitchEntry(target, blockStm.get(0), last);
            }
            return extractSwitchStructure(target, body, replacements, last);
        }
        return extractOneSwitchEntry(target, body, last);
    }

    /*
     * Other than in the prescribed place, our lvalue can't be touched.
     */
    static class UsageCheck extends AbstractExpressionRewriter {
        private final LValue target;
        private boolean failed;

        UsageCheck(LValue target) {
            this.target = target;
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (target.equals(lValue)) {
                failed = true;
            }
            return super.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
        }
    }

    enum LastOk {
        Ok,
        OkIfLast,
        NotOk
    }

    static class SwitchExpressionTransformer implements StructuredStatementTransformer {
        private BadSwitchExpressionTransformer badTransfomer = new BadSwitchExpressionTransformer();
        private UsageCheck rewriter;
        private List<Pair<Op04StructuredStatement, StructuredStatement>> replacements;
        private final LValue target;
        private LastOk lastOk = LastOk.NotOk;
        private boolean failed;
        private Expression pendingAssignment;

        private SwitchExpressionTransformer(LValue target, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements) {
            this.target = target;
            this.rewriter = new UsageCheck(target);
            this.replacements = replacements;
        }

        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (failed) return in;
            lastOk = LastOk.NotOk;
            if (pendingAssignment != null) {
                // Expecting a break.
                if (in instanceof StructuredBreak) {
                    if (((StructuredBreak) in).isLocalBreak()) {
                        replacements.add(Pair.make(in.getContainer(), (StructuredStatement)new StructuredExpressionBreak(pendingAssignment)));
                        pendingAssignment = null;
                        lastOk = LastOk.Ok;
                        return in;
                    }
                }
                failed = true;
                return in;
            }
            if (in.supportsBreak()) {
                return badTransfomer.transform(in, scope);
            }
            if (in instanceof StructuredBreak) {
                failed = true;
                return in;
            }
            if (in instanceof StructuredReturn) {
                failed = true;
                return in;
            }
            if (in instanceof StructuredAssignment && ((StructuredAssignment) in).getLvalue().equals(target)) {
                if (pendingAssignment != null) {
                    failed = true;
                    return in;
                }
                pendingAssignment = ((StructuredAssignment) in).getRvalue();
                replacements.add(Pair.make(in.getContainer(), (StructuredStatement)StructuredComment.EMPTY_COMMENT));
                lastOk = LastOk.OkIfLast;
                return in;
            }
            in.rewriteExpressions(rewriter);
            if (rewriter.failed) {
                failed = true;
                return in;
            }
            in.transformStructuredChildren(this, scope);
            return in;
        }

        // Inside here, we can't assign, we can't even break too far.
        class BadSwitchExpressionTransformer implements StructuredStatementTransformer {
            @Override
            public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
                if (failed) return in;
                lastOk = LastOk.NotOk;
                in.rewriteExpressions(rewriter);
                if (rewriter.failed) {
                    failed = true;
                    return in;
                }
                if (in instanceof StructuredBreak) {
                    // this *COULD* be ok, but come on.....
                    if (!((StructuredBreak) in).isLocalBreak()) {
                        failed = true;
                    }
                    return in;
                }
                if (in instanceof StructuredReturn) {
                    failed = true;
                    return in;
                }
                in.transformStructuredChildren(this, scope);
                return in;
            }
        }
    }

    private Expression extractSwitchStructure(LValue target, Op04StructuredStatement body, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements, boolean last) {
        SwitchExpressionTransformer transformer = new SwitchExpressionTransformer(target, replacements);
        body.transform(transformer, new StructuredScope());
        if (transformer.failed) return null;
        if (transformer.lastOk == LastOk.NotOk) return null;
        if (transformer.lastOk == LastOk.OkIfLast) {
            if (!last) return null;
        }
        return new StructuredStatementExpression(target.getInferredJavaType(), body.getStatement());
    }

    private Expression extractOneSwitchAssignment(LValue target, List<Op04StructuredStatement> blockStm) {
        if (blockStm.size() != 2) {
            return null;
        }
        if (!(blockStm.get(1).getStatement() instanceof StructuredBreak)) {
            return null;
        }
        return extractJustSwitchAssignment(target, blockStm.get(0));
    }

    private Expression extractOneSwitchEntry(LValue target, Op04StructuredStatement body, boolean last) {
        StructuredStatement content = body.getStatement();
        if (content instanceof StructuredThrow) {
            return new StructuredStatementExpression(new InferredJavaType(TypeConstants.THROWABLE, InferredJavaType.Source.TEST), content);
        }
        if (!last) {
            return null;
        }
        return extractJustSwitchAssignment(target, body);
    }

    private Expression extractJustSwitchAssignment(LValue target, Op04StructuredStatement body) {
        StructuredStatement assign = body.getStatement();
        if (!(assign instanceof StructuredAssignment)) {
            return null;
        }
        StructuredAssignment assignStm = (StructuredAssignment)assign;
        if (!assignStm.getLvalue().equals(target)) {
            return null;
        }
        return assignStm.getRvalue();
    }
}
