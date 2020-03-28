package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.ExpressionRewriterTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SwitchExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CommentStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SwitchExpressionRewriter extends AbstractExpressionRewriter implements StructuredStatementTransformer {
    private final boolean experimental;
    private DecompilerComments comments;
    private Map<StructuredStatement, List<Op04StructuredStatement>> blockSwitches = MapFactory.newOrderedMap();

    public SwitchExpressionRewriter(DecompilerComments comments, ClassFileVersion classFileVersion) {
        this.comments = comments;
        this.experimental = OptionsImpl.switchExpressionVersion.isExperimentalIn(classFileVersion);
    }

    public void transform(Op04StructuredStatement root) {
        root.transform(this, new StructuredScope());
        rewriteBlockSwitches(root);
    }

    private static class LValueSingleUsageCheckingRewriter extends AbstractExpressionRewriter {
        Map<LValue, Boolean> usages = MapFactory.newMap();
        Map<LValue, Op04StructuredStatement> usageSites = MapFactory.newMap();

        private Set<StatementContainer> creators;

        LValueSingleUsageCheckingRewriter(Set<StatementContainer> creators) {
            this.creators = creators;
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            Boolean prev = usages.get(lValue);
            if (prev == Boolean.FALSE) {
                return lValue;
            } else if (prev == null) {
                if (creators.contains(statementContainer)) return lValue;
                usages.put(lValue, Boolean.TRUE);
                usageSites.put(lValue, (Op04StructuredStatement)statementContainer);
            } else {
                usages.put(lValue, Boolean.FALSE);
            }
            return lValue;
        }
    }

    private void rewriteBlockSwitches(Op04StructuredStatement root) {
        if (blockSwitches.isEmpty()) return;

        Set<StatementContainer> creators = SetFactory.newSet();
        for (List<Op04StructuredStatement> list : blockSwitches.values()) {
            creators.addAll(list);
        }
        LValueSingleUsageCheckingRewriter scr = new LValueSingleUsageCheckingRewriter(creators);
        root.transform(new ExpressionRewriterTransformer(scr), new StructuredScope());

        for (Map.Entry<StructuredStatement, List<Op04StructuredStatement>> entry : blockSwitches.entrySet()) {
            List<Op04StructuredStatement> switches = entry.getValue();
            StructuredStatement stm = entry.getKey();
            if (!(stm instanceof Block)) continue;

            List<Op04StructuredStatement> statements = ((Block) stm).getBlockStatements();

            Set<Op04StructuredStatement> usages = SetFactory.newOrderedSet();
            Set<Op04StructuredStatement> swtchSet = SetFactory.newSet();
            for (Op04StructuredStatement swtch : switches) {
                if (!(swtch.getStatement() instanceof StructuredAssignment)) continue;
                StructuredAssignment sa = (StructuredAssignment)swtch.getStatement();
                if (!sa.isCreator(sa.getLvalue())) continue;
                swtchSet.add(swtch);
                Op04StructuredStatement usage = scr.usageSites.get(sa.getLvalue());
                if (usage == null) continue;
                usages.add(usage);
            }

            for (Op04StructuredStatement usage : usages) {
                int usageIdx = statements.indexOf(usage);

                for (int x = usageIdx-1;x>=0;--x) {
                    Op04StructuredStatement backstm = statements.get(x);
                    if (backstm.getStatement().isEffectivelyNOP()) continue;
                    if (swtchSet.contains(backstm)) {
                        StructuredStatement stss = backstm.getStatement();
                        if (!(stss instanceof StructuredAssignment)) {
                            // This should not happen, but if we have a multiple rewrite?
                            break;
                        }
                        StructuredAssignment sa = (StructuredAssignment)stss;
                        ExpressionReplacingRewriter err = new ExpressionReplacingRewriter(new LValueExpression(sa.getLvalue()), sa.getRvalue());
                        usage.getStatement().rewriteExpressions(err);
                        backstm.nopOut();
                        continue;
                    }
                    break;
                }
            }
        }
    }



    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        if (in instanceof StructuredSwitch) {
            Op04StructuredStatement container = in.getContainer();
            rewrite(container, scope);
            return container.getStatement();
        }
        return in;
    }

    // TODO : This is a very common pattern - linearize is treated as a util - we should just walk.
    public void rewrite(Op04StructuredStatement root, StructuredScope scope) {
        // While we're linearising inside a recursion here, this isn't as bad as it looks, because we only
        // do it for switch statements.
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        if (replaceSwitch(root, structuredStatements, scope) && experimental) {
            comments.addComment(DecompilerComment.EXPERIMENTAL_FEATURE);
        }
    }

    private boolean replaceSwitch(Op04StructuredStatement container, List<StructuredStatement> structuredStatements, StructuredScope scope) {
        StructuredStatement swat = structuredStatements.get(0);
        if (!(swat instanceof StructuredSwitch)) {
            return false;
        }
        StructuredSwitch swatch = (StructuredSwitch)swat;
        // At this point, the switch needs total coverage, and every item needs to assign
        // a single thing to target, or throw an exception;
        Op04StructuredStatement swBody = swatch.getBody();
        if (!(swBody.getStatement() instanceof Block)) {
            return false;
        }
        Block b = (Block)swBody.getStatement();
        List<Op04StructuredStatement> content = b.getBlockStatements();
        int size = content.size();
        List<Pair<StructuredCase, Expression>> extracted = ListFactory.newList();
        List<Pair<Op04StructuredStatement, StructuredStatement>> replacements = ListFactory.newList();
        LValue target = null;
        for (int itm = 0; itm < size && target == null; ++itm) {
            target = extractSwitchLValue(content.get(itm), itm == size - 1);
        }
        if (target == null) {
            return false;
        }
        for (int itm = 0; itm < size; ++itm) {
            Pair<StructuredCase, Expression> e = extractSwitchEntryPair(target, content.get(itm), replacements,itm == size -1);
            if (e == null) {
                return false;
            }
            extracted.add(e);
        }
        /*
         * We have to find definition of target in our scope.
         */
        StructuredStatement declarationContainer = scope.get(1);
        if (!(declarationContainer instanceof Block)) return false;

        // Find the definition of the var, and ensure it's not used between there and statement.
        // TODO : This is expensive, and we could improve this by ensuring variable is declared
        // closer to usage.
        List<Op04StructuredStatement> blockContent = ((Block) declarationContainer).getBlockStatements();
        Op04StructuredStatement definition = null;
        UsageCheck usageCheck = new UsageCheck(target);
        for (Op04StructuredStatement blockItem : blockContent) {
            if (definition == null) {
                StructuredStatement stm = blockItem.getStatement();
                if (stm instanceof StructuredDefinition) {
                    if (target.equals(((StructuredDefinition) stm).getLvalue())) {
                        definition = blockItem;
                    }
                }
                continue;
            }
            if (blockItem == container) break;
            blockItem.getStatement().rewriteExpressions(usageCheck);
            if (usageCheck.failed) {
                return false;
            }
        }
        if (definition == null) {
            return false;
        }

        // Now we're sure we're doing the transformation....
        for (Pair<Op04StructuredStatement, StructuredStatement> replacement : replacements) {
            replacement.getFirst().replaceStatement(replacement.getSecond());
        }
        List<SwitchExpression.Branch> items = ListFactory.newList();
        for (Pair<StructuredCase, Expression> e : extracted) {
            items.add(new SwitchExpression.Branch(e.getFirst().getValues(), e.getSecond()));
        }

        definition.nopOut();
        StructuredAssignment switchStatement =
                new StructuredAssignment(target, new SwitchExpression(target.getInferredJavaType(), swatch.getSwitchOn(), items));
        swat.getContainer().replaceStatement(switchStatement);
        Op04StructuredStatement switchStatementContainer = switchStatement.getContainer();
        switchStatement.markCreator(target, switchStatementContainer);
        StructuredStatement parent = scope.get(1);
        if (parent != null) {
            List<Op04StructuredStatement> targetPairs = blockSwitches.get(parent);
            if (targetPairs == null) {
                targetPairs = ListFactory.newList();
                blockSwitches.put(parent, targetPairs);
            }
            targetPairs.add(switchStatementContainer);
        }
        return true;
    }

    private LValue extractSwitchLValue(Op04StructuredStatement item, boolean last) {
        StructuredStatement stm = item.getStatement();
        if (!(stm instanceof StructuredCase)) {
            return null;
        }
        StructuredCase sc = (StructuredCase)stm;
        Op04StructuredStatement body = sc.getBody();
        StructuredStatement bodyStm = body.getStatement();
        List<Op04StructuredStatement> content;
        if (bodyStm instanceof Block) {
            content = Functional.filterOptimistic(((Block) bodyStm).getBlockStatements(), notEmpty);
        } else {
            content = Collections.singletonList(body);
        }
        if (content.size() > 2) {
            content = content.subList(content.size()-2,content.size());
        }
        if (content.isEmpty()) return null;
        if (content.size() == 2) {
            // last should be a break.
            if (content.get(1).getStatement() instanceof StructuredBreak) {
                // and the assignment?
                StructuredStatement isAssign = content.get(0).getStatement();
                if (!(isAssign instanceof StructuredAssignment)) return null;
                return ((StructuredAssignment) isAssign).getLvalue();
            }
        }
        if (!last) return null;
        // and the assignment?
        StructuredStatement isAssign = content.get(content.size()-1).getStatement();
        if (!(isAssign instanceof StructuredAssignment)) return null;
        return ((StructuredAssignment) isAssign).getLvalue();
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
                        replacements.add(Pair.make(in.getContainer(), (StructuredStatement)new StructuredExpressionYield(pendingAssignment)));
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
            int lastReplacement = replacements.size() - 1;
            Op04StructuredStatement stm = replacements.get(lastReplacement).getFirst();
            replacements.set(lastReplacement, Pair.make(stm, (StructuredStatement)new StructuredExpressionYield(transformer.pendingAssignment)));
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
