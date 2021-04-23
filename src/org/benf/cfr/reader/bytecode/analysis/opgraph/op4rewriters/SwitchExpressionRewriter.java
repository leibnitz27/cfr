package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.ExpressionRewriterTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SwitchExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CommentStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredBreak;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCase;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredDefinition;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionYield;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredSwitch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredThrow;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SwitchExpressionRewriter extends AbstractExpressionRewriter implements StructuredStatementTransformer {
    private final boolean experimental;
    private final Method method;
    private DecompilerComments comments;
    private final Set<StructuredStatement> classifiedEmpty = SetFactory.newIdentitySet();

    public SwitchExpressionRewriter(DecompilerComments comments, Method method) {
        this.comments = comments;
        this.experimental = OptionsImpl.switchExpressionVersion.isExperimentalIn(method.getClassFile().getClassFileVersion());
        this.method = method;
    }

    public void transform(Op04StructuredStatement root) {
        doTransform(root);
        doAggressiveTransforms(root);
        rewriteBlockSwitches(root);
    }

    private void doTransform(Op04StructuredStatement root) {
        root.transform(this, new StructuredScope());
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
                usageSites.remove(lValue);
            }
            return lValue;
        }
    }

    private static class BlockSwitchDiscoverer implements StructuredStatementTransformer {
        Map<StructuredStatement, List<Op04StructuredStatement>> blockSwitches = MapFactory.newOrderedMap();

        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {

            if (in instanceof StructuredAssignment && ((StructuredAssignment) in).getRvalue() instanceof SwitchExpression) {
                Op04StructuredStatement switchStatementContainer = in.getContainer();
                StructuredStatement parent = scope.get(0);
                if (parent != null) {
                    List<Op04StructuredStatement> targetPairs = blockSwitches.get(parent);
                    if (targetPairs == null) {
                        targetPairs = ListFactory.newList();
                        blockSwitches.put(parent, targetPairs);
                    }
                    targetPairs.add(switchStatementContainer);
                }
            }

            in.transformStructuredChildren(this, scope);
            return in;
        }
    }

    private void rewriteBlockSwitches(Op04StructuredStatement root) {
        BlockSwitchDiscoverer bsd = new BlockSwitchDiscoverer();
        root.transform(bsd, new StructuredScope());

        if (bsd.blockSwitches.isEmpty()) return;

        Set<StatementContainer> creators = SetFactory.newSet();
        for (List<Op04StructuredStatement> list : bsd.blockSwitches.values()) {
            creators.addAll(list);
        }
        LValueSingleUsageCheckingRewriter scr = new LValueSingleUsageCheckingRewriter(creators);
        root.transform(new ExpressionRewriterTransformer(scr), new StructuredScope());

        for (Map.Entry<StructuredStatement, List<Op04StructuredStatement>> entry : bsd.blockSwitches.entrySet()) {
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
            target = extractSwitchLValue(swatch.getBlockIdentifier(), content.get(itm), itm == size - 1);
        }
        if (target == null) {
            return false;
        }
        for (int itm = 0; itm < size; ++itm) {
            Pair<StructuredCase, Expression> e = extractSwitchEntryPair(target, swatch.getBlockIdentifier(), content.get(itm), replacements,itm == size -1);
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
                new StructuredAssignment(BytecodeLoc.TODO, target, new SwitchExpression(BytecodeLoc.TODO, target.getInferredJavaType(), swatch.getSwitchOn(), items));
        swat.getContainer().replaceStatement(switchStatement);
        Op04StructuredStatement switchStatementContainer = switchStatement.getContainer();
        switchStatement.markCreator(target, switchStatementContainer);
        return true;
    }

    private static class SwitchExpressionSearcher implements StructuredStatementTransformer {
        StructuredStatement last = null;
        LValue found = null;
        private BlockIdentifier blockIdentifier;

        SwitchExpressionSearcher(BlockIdentifier blockIdentifier) {
            this.blockIdentifier = blockIdentifier;
        }

        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (found != null) {
                return in;
            }

            if (in instanceof Block) {
                in.transformStructuredChildren(this, scope);
                return in;
            }
            if (in instanceof StructuredBreak) {
                if (blockIdentifier.equals(((StructuredBreak) in).getBreakBlock())) {
                    checkLast();
                    return in;
                }
            }
            if (!in.isEffectivelyNOP()) {
                last = in;
            }
            in.transformStructuredChildren(this, scope);
            return in;
        }

        private void checkLast() {
            if (last instanceof StructuredAssignment) {
                found = ((StructuredAssignment) last).getLvalue();
            }
        }
    }

    private LValue extractSwitchLValue(BlockIdentifier blockIdentifier, Op04StructuredStatement item, boolean last) {
        SwitchExpressionSearcher ses = new SwitchExpressionSearcher(blockIdentifier);
        item.transform(ses, new StructuredScope());
        if (ses.found != null) {
            return ses.found;
        }
        if (last) ses.checkLast();
        return ses.found;
    }

    private final static Predicate<Op04StructuredStatement> notEmpty = new Predicate<Op04StructuredStatement>() {
        @Override
        public boolean test(Op04StructuredStatement in) {
            return !(in.getStatement() instanceof Nop || in.getStatement() instanceof CommentStatement);
        }
    };

    private Pair<StructuredCase, Expression> extractSwitchEntryPair(LValue target, BlockIdentifier blockIdentifier, Op04StructuredStatement item, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements, boolean last) {
        StructuredStatement stm = item.getStatement();
        if (!(stm instanceof StructuredCase)) {
            return null;
        }
        StructuredCase sc = (StructuredCase)stm;
        Expression res = extractSwitchEntry(target, blockIdentifier, sc.getBody(), replacements, last);
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
    private Expression extractSwitchEntry(LValue target, BlockIdentifier blockIdentifier, Op04StructuredStatement body, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements, boolean last) {
        SwitchExpressionTransformer transformer = new SwitchExpressionTransformer(target, blockIdentifier, replacements, last);
        body.transform(transformer, new StructuredScope());
        if (transformer.failed) return null;
        if (!transformer.lastMarked) return null;
        if (transformer.lastAssign && !last) return null;
        if (transformer.totalStatements == 1 && transformer.singleValue != null) {
            return transformer.singleValue;
        }

        return new StructuredStatementExpression(target.getInferredJavaType(), body.getStatement());
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

    static class SwitchExpressionTransformer implements StructuredStatementTransformer {
        private UsageCheck rewriter;
        private BlockIdentifier blockIdentifier;
        private List<Pair<Op04StructuredStatement, StructuredStatement>> replacements;
        private boolean last;
        private final LValue target;
        private boolean failed;
        private boolean lastAssign = true;
        private boolean lastMarked = false;
        private Expression singleValue = null;
        private int totalStatements;

        private SwitchExpressionTransformer(LValue target, BlockIdentifier blockIdentifier, List<Pair<Op04StructuredStatement, StructuredStatement>> replacements, boolean last) {
            this.target = target;
            this.rewriter = new UsageCheck(target);
            this.blockIdentifier = blockIdentifier;
            this.replacements = replacements;
            this.last = last;
        }

        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (failed) return in;
            lastMarked = false;
            lastAssign = true;

            if (in.isEffectivelyNOP()) return in;

            if (!(in instanceof Block)) {
                totalStatements++;
            }

            if (in instanceof StructuredBreak) {
                BreakClassification bk = classifyBreak((StructuredBreak)in, scope);
                switch (bk) {
                    case CORRECT:
                        lastMarked = true;
                        lastAssign = false;
                        totalStatements--;
                        replacements.add(Pair.make(in.getContainer(),(StructuredStatement)StructuredComment.EMPTY_COMMENT));
                        return in;
                    case INNER:
                        break;
                    case TOO_FAR:
                        failed = true;
                        return in;
                }
            }

            if (in instanceof StructuredReturn) {
                failed = true;
                return in;
            }

            if (in instanceof StructuredAssignment) {
                if (((StructuredAssignment) in).getLvalue().equals(target)) {
                    Set<Op04StructuredStatement> nextFallThrough = scope.getNextFallThrough(in);
                    lastMarked = true;
                    replacements.add(Pair.make(in.getContainer(), (StructuredStatement)new StructuredExpressionYield(BytecodeLoc.TODO, ((StructuredAssignment) in).getRvalue())));
                    singleValue = ((StructuredAssignment) in).getRvalue();
                    boolean foundBreak = false;
                    for (Op04StructuredStatement fall : nextFallThrough) {
                        StructuredStatement fallStatement = fall.getStatement();
                        if (fallStatement.isEffectivelyNOP()) continue;
                        if (fallStatement instanceof StructuredBreak) {
                            BreakClassification bk = classifyBreak((StructuredBreak)fallStatement, scope);
                            if (bk != BreakClassification.CORRECT) {
                                failed = true;
                                return in;
                            } else {
                                foundBreak = true;
                            }
                        } else {
                            failed = true;
                            return in;
                        }
                    }
                    if (!last && !foundBreak) {
                        failed = true;
                        return in;
                    }
                    return in;
                }
            }

            if (in instanceof StructuredThrow) {
                replacements.add(Pair.make(in.getContainer(), in));
                singleValue = new StructuredStatementExpression(target.getInferredJavaType(), in);
                lastAssign = false;
                lastMarked = true;
            }

            in.rewriteExpressions(rewriter);
            if (rewriter.failed) {
                failed = true;
                return in;
            }
            in.transformStructuredChildren(this, scope);
            return in;
        }

        BreakClassification classifyBreak(StructuredBreak in, StructuredScope scope) {
            BlockIdentifier breakBlock = in.getBreakBlock();
            if (breakBlock == blockIdentifier) return BreakClassification.CORRECT;
            for (StructuredStatement stm : scope.getAll()) {
                BlockIdentifier block = stm.getBreakableBlockOrNull();
                if (block == breakBlock) return BreakClassification.INNER;
            }
            return BreakClassification.TOO_FAR;
        }

        enum BreakClassification {
            CORRECT,
            TOO_FAR,
            INNER
        }
    }


    /*
     * There are times when we want to eliminate intermediate steps - i.e. when constructing an anonymous
     * array, or when passing arguments to a chained constructor.
     *
     * We can (if we have to!) perform the following somewhat crazy code 'reductions'
     * (currently only considered before constructor chains).
     *
     * 1) //////////////
     * switch (A) {
     *    default:
     * }
     * X : NON SWITCH EXPRESSION
     *
     * -->
     *
     * switch (A) {
     *    default:
     *      Y
     *      X
     * }
     *
     * This can be rolled uptil the first initialisation, at which point it becomes a case of 2.
     *
     * 2) ///////////////
     *
     * switch (A) {
     *    default:
     * }
     * r = switch (B) {
     * }
     *
     * --->
     *
     * r = switch (A) {
     *   default -> switch (B) {
     *   }
     * }
     *
     * 3) //////////////// (by far the ugliest).  Unless
     *
     * r = switch (A) {
     *    XXXXXX
     * }
     * switch (B) {
     *    default:
     * }
     *
     * -->
     *
     * r = switch (0) {
     *    default -> {
     *       var tmp = switch (A) {
     *          xxx
     *       };
     *       var ignore0 = B;
     *       yield tmp;
     *    }
     * }
     */
    private RollState getRollState(Op04StructuredStatement body) {
        StructuredStatement s = body.getStatement();
        if (!(s instanceof Block)) return new RollState();
        Block b = (Block)s;

        List<Op04StructuredStatement> prequel = ListFactory.newList();
        LinkedList<ClassifiedStm> tt = ListFactory.newLinkedList();
        List<Op04StructuredStatement> others = ListFactory.newList();
        Iterator<Op04StructuredStatement> it = b.getBlockStatements().iterator();
        Set<Expression> directs = SetFactory.newSet();
        boolean found = false;
        boolean inPrequel = method.getClassFile().isInnerClass();
        while (it.hasNext()) {
            Op04StructuredStatement item = it.next();
            if (item.getStatement().isEffectivelyNOP()) continue;
            if (inPrequel) {
                if (prequelAssign(item, directs)) {
                    prequel.add(item);
                    continue;
                } else {
                    inPrequel = false;
                }
            }
            ClassifiedStm type = classify(item);
            if (type.type == ClassifyType.CHAINED_CONSTRUCTOR) {
                others.add(item);
                while (it.hasNext()) {
                    others.add(it.next());
                }
                found = true;
            } else {
                tt.add(type);
            }
        }
        if (!found) return new RollState();
        return new RollState(prequel, tt, others, b, directs);
    }

    private boolean prequelAssign(Op04StructuredStatement item, Set<Expression> directs) {
        StructuredStatement s = item.getStatement();
        if (!(s instanceof StructuredAssignment)) return false;
        LValue lv = ((StructuredAssignment) s).getLvalue();
        if (!(lv instanceof FieldVariable)) return false;
        FieldVariable fv = (FieldVariable)lv;
        if (!fv.objectIsThis()) return false;
        directs.add(new LValueExpression(lv));
        directs.add(((StructuredAssignment) s).getRvalue());
        return true;
    }

    class RollState {
        boolean valid;
        List<Op04StructuredStatement> prequel;
        LinkedList<ClassifiedStm> switchdata;
        List<Op04StructuredStatement> remainder;
        Block block;
        private Set<Expression> directs;

        RollState() {
            this.valid = false;
        }

        RollState(List<Op04StructuredStatement> prequel, LinkedList<ClassifiedStm> switchdata, List<Op04StructuredStatement> remainder, Block block, Set<Expression> directs) {
            this.directs = directs;
            this.valid = true;
            this.prequel = prequel;
            this.switchdata = switchdata;
            this.remainder = remainder;
            this.block = block;
        }
    }

    private boolean rollOne(Op04StructuredStatement root, UnaryFunction<RollState, Boolean> apply) {
        RollState rollState = getRollState(root);
        if (!rollState.valid) return false;
        if (apply.invoke(rollState)) {
            List<Op04StructuredStatement> mutableBlockStatements = rollState.block.getBlockStatements();
            mutableBlockStatements.clear();
            mutableBlockStatements.addAll(rollState.prequel);
            for (ClassifiedStm t : rollState.switchdata) {
                mutableBlockStatements.add(t.stm);
            }
            mutableBlockStatements.addAll(rollState.remainder);
            doTransform(root);
        }
        return true;
    }

    private void doAggressiveTransforms(Op04StructuredStatement root) {
        if (!method.isConstructor()) return;

        if (!rollOne(root, new UnaryFunction<RollState, Boolean>() {
            @Override
            public Boolean invoke(RollState arg) {
                return rollUpEmptySwitches(arg);
            }
        })) return;
        rollOne(root, new UnaryFunction<RollState, Boolean>() {
            @Override
            public Boolean invoke(RollState arg) {
                return rollUpEmptySwitchCreation(arg);
            }
        });
        rollOne(root, new UnaryFunction<RollState, Boolean>() {
            @Override
            public Boolean invoke(RollState arg) {
                return rollUpEmptySwitchAggregation(arg);
            }
        });
        // As a final pass - if there's a single 'default switch' remaining (SwitchConstructorTest1,6)
        // capture the first argument to the chain.
        rollOne(root, new UnaryFunction<RollState, Boolean>() {
            @Override
            public Boolean invoke(RollState arg) {
                return rollSingleDefault(arg);
            }
        });

    }

    /*
        switch (b) {
            default: {
                System.out.println("Hello world");
                if (b < 3) {
                    System.out.println("one");
                } else {
                    System.out.println("two");
                }
            }
        }
        this(2);

        ->>

       int footmp;
       switch (b) {
            default: {
                System.out.println("Hello world");
                if (b < 3) {
                    System.out.println("one");
                } else {
                    System.out.println("two");
                }
            }
            footmp = 2;
        }
        this(footmp);

     */
    private boolean rollSingleDefault(RollState rollState) {
        if (rollState.switchdata.size() != 1) return false;
        ClassifiedStm t = rollState.switchdata.get(0);
        if (t.type != ClassifyType.EMPTY_SWITCH) return false;

        // Try to extract the first expression from the call.
        if (rollState.remainder.isEmpty()) return false;
        Op04StructuredStatement call = rollState.remainder.get(0);
        StructuredStatement s = call.getStatement();
        if (!(s instanceof StructuredExpressionStatement)) return false;
        Expression e = ((StructuredExpressionStatement) s).getExpression();
        List<Expression> args = null;
        if (e instanceof MemberFunctionInvokation && ((MemberFunctionInvokation) e).isInitMethod()) {
            args = ((MemberFunctionInvokation) e).getArgs();
        } else if (e instanceof SuperFunctionInvokation) {
            args = ((SuperFunctionInvokation) e).getArgs();
        }
        if (args == null) return false;
        // We have to use the first NON hidden arg - however (of course) we don't necessarily know that here!
        int idx;
        for (idx=0;idx<args.size();++idx) {
            if (!rollState.directs.contains(args.get(idx))) break;
        }
        if (idx >= args.size()) return false;
        Expression tmpValue = args.get(idx);
        LValue tmp = new LocalVariable("cfr_switch_hack2", tmpValue.getInferredJavaType());
        rollState.switchdata.add(0, new ClassifiedStm(ClassifyType.DEFINITION, new Op04StructuredStatement(new StructuredDefinition(tmp))));
        addToSwitch(t.stm, new Op04StructuredStatement(new StructuredAssignment(BytecodeLoc.TODO, tmp, tmpValue)));
        args.set(idx, new LValueExpression(tmp));
        return true;
    }

    private boolean rollUpEmptySwitchAggregation(RollState rollState) {
        LinkedList<ClassifiedStm> tt = rollState.switchdata;
        Iterator<ClassifiedStm> di = tt.descendingIterator();
        ClassifiedStm last = null;
        boolean doneWork = false;
        while (di.hasNext()) {
            ClassifiedStm curr = di.next();
            if (last != null) {
                if (curr.type == ClassifyType.SWITCH_EXPRESSION) {
                    if (last.type == ClassifyType.EMPTY_SWITCH) { // this could work for 'other' too....
                        combineSwitchExpressionWithOther(curr, last);
                        di.remove();
                        doneWork = true;
                        continue;
                    }
                }
            }
            last = curr;
        }
        return doneWork;
    }

    /*  This is by far the ugliest - we introduce a switch statement :(

        byte by = switch (0) {
            default -> b;
            1 -> 12;
        };
        switch (0) {
            default: {
                System.out.println("HERE");
            }
        }

        -->

        byte by = switch (0) {
            default -> {
                byte tmp = switch (0) {
                    default -> b;
                    1 -> 12;
                };
                switch (0) {
                    default: {
                        System.out.println("HERE");
                    }
                }
                yield tmp;
            }
        };

     */
    private void combineSwitchExpressionWithOther(ClassifiedStm switchExpression, ClassifiedStm other) {
        StructuredAssignment assignment = (StructuredAssignment)switchExpression.stm.getStatement();
        LValue lv = assignment.getLvalue();
        Expression se = assignment.getRvalue();
        LinkedList<Op04StructuredStatement> newBlockContent = ListFactory.newLinkedList();
        LValue tmp = new LocalVariable("cfr_switch_hack", lv.getInferredJavaType());
        newBlockContent.add(new Op04StructuredStatement(new StructuredAssignment(BytecodeLoc.TODO, tmp, se, true)));
        newBlockContent.add(other.stm);
        newBlockContent.add(new Op04StructuredStatement(new StructuredExpressionYield(BytecodeLoc.TODO, new LValueExpression(tmp))));
        Block newBlock = new Block(newBlockContent, true);
        SwitchExpression nse = new SwitchExpression(BytecodeLoc.TODO, lv.getInferredJavaType(), Literal.INT_ZERO, Collections.singletonList(new SwitchExpression.Branch(Collections.<Expression>emptyList(), new StructuredStatementExpression(lv.getInferredJavaType(), newBlock))));
        StructuredAssignment nsa = new StructuredAssignment(BytecodeLoc.TODO, lv, nse, true);
        other.type = ClassifyType.SWITCH_EXPRESSION;
        other.stm = new Op04StructuredStatement(nsa);
    }

    private boolean rollUpEmptySwitchCreation(RollState rollState) {
        LinkedList<ClassifiedStm> tt = rollState.switchdata;
        Iterator<ClassifiedStm> di = tt.descendingIterator();
        ClassifiedStm last = null;
        boolean doneWork = false;
        while (di.hasNext()) {
            ClassifiedStm curr = di.next();
            if (curr.type == ClassifyType.EMPTY_SWITCH) {
                if (last != null) {
                    if (last.type == ClassifyType.OTHER_CREATION || last.type == ClassifyType.SWITCH_EXPRESSION) {
                        combineEmptySwitchWithCreation(curr, last);
                        doneWork = true;
                        continue;

                    }
                }
            }
            last = curr;
        }
        return doneWork;
    }

    /*
        switch (0) {
            default:
        }
        byte by = b;

        ->

        byte by
        switch(0) {
           default:
              by = b;
        }
     */
    private void combineEmptySwitchWithCreation(ClassifiedStm switchStm, ClassifiedStm assignStm) {
        StructuredAssignment stm = (StructuredAssignment)assignStm.stm.getStatement();
        Expression rhs = stm.getRvalue();
        LValue lhs = stm.getLvalue();
        addToSwitch(switchStm.stm, new Op04StructuredStatement(new StructuredAssignment(BytecodeLoc.TODO, lhs, rhs)));
        StructuredStatement swtch = switchStm.stm.getStatement();
        assignStm.stm.replaceStatement(swtch);
        assignStm.type = ClassifyType.EMPTY_SWITCH;
        switchStm.stm.replaceStatement(new StructuredDefinition(lhs));
        switchStm.type = ClassifyType.DEFINITION;
    }

    private boolean rollUpEmptySwitches(RollState rollState) {
        LinkedList<ClassifiedStm> tt = rollState.switchdata;
        List<ClassifiedStm> lt = ListFactory.newList(tt);

        boolean doneWork = false;
        for (int x=lt.size()-2;x>=0;--x) {

            ClassifiedStm curr = lt.get(x);
            ClassifiedStm last = x+1 < lt.size() ? lt.get(x+1) : null;
            if (curr.type == ClassifyType.EMPTY_SWITCH) {
                if (last != null) {
                    if (last.type == ClassifyType.OTHER || last.type == ClassifyType.EMPTY_SWITCH) {
                        addToSwitch(curr.stm, last.stm);
                        last.stm = curr.stm;
                        last.type = ClassifyType.EMPTY_SWITCH;
                        lt.remove(x);
                        ++x;
                        doneWork = true;
                    }
                }
            }
        }
        if (doneWork) {
            rollState.switchdata.clear();
            rollState.switchdata.addAll(lt);
        }
        return doneWork;
    }

    private void addToSwitch(Op04StructuredStatement swtch, Op04StructuredStatement add) {
        StructuredSwitch ss = (StructuredSwitch)swtch.getStatement();
        Block block = (Block)ss.getBody().getStatement();
        StructuredCase caseStm = (StructuredCase)(block.getOneStatementIfPresent().getSecond().getStatement());
        Block block1 = (Block) caseStm.getBody().getStatement();
        block1.setIndenting(true);
        block1.addStatement(add);
    }

    private class ClassifiedStm {
        ClassifyType type;
        Op04StructuredStatement stm;

        ClassifiedStm(ClassifyType type, Op04StructuredStatement stm) {
            this.type = type;
            this.stm = stm;
        }

        @Override
        public String toString() {
            return "{" +
                    "" + type +
                    ", " + stm +
                    '}';
        }
    }

    private ClassifiedStm classify(Op04StructuredStatement item) {
        StructuredStatement stm = item.getStatement();
        if (stm instanceof StructuredDefinition) {
            return new ClassifiedStm(ClassifyType.DEFINITION, item);
        }
        if (stm instanceof StructuredAssignment) {
            StructuredAssignment as = (StructuredAssignment)stm;
            LValue lv = as.getLvalue();
            if (!as.isCreator(lv)) {
                return new ClassifiedStm(ClassifyType.OTHER, item);
            }
            Expression rv = as.getRvalue();
            if (rv instanceof SwitchExpression) {
                return new ClassifiedStm(ClassifyType.SWITCH_EXPRESSION, item);
            } else {
                return new ClassifiedStm(ClassifyType.OTHER_CREATION, item);
            }
        }
        if (stm instanceof StructuredSwitch) {
            StructuredSwitch ss = (StructuredSwitch)stm;
            if (ss.isOnlyEmptyDefault() || classifiedEmpty.contains(ss)) {
                classifiedEmpty.add(ss);
                return new ClassifiedStm(ClassifyType.EMPTY_SWITCH, item);
            } else {
                return new ClassifiedStm(ClassifyType.OTHER, item);
            }
        }
        if (isConstructorChain(item)) {
            return new ClassifiedStm(ClassifyType.CHAINED_CONSTRUCTOR, item);
        }
        return new ClassifiedStm(ClassifyType.OTHER, item);
    }

    private enum ClassifyType {
        NONE,
        EMPTY_SWITCH,
        SWITCH_EXPRESSION,
        OTHER_CREATION,
        CHAINED_CONSTRUCTOR,
        DEFINITION,
        OTHER
    }

    // TODO : Or super!!!
    private boolean isConstructorChain(Op04StructuredStatement item) {
        StructuredStatement s = item.getStatement();
        if (!(s instanceof StructuredExpressionStatement)) return false;
        Expression e = ((StructuredExpressionStatement) s).getExpression();
        if (e instanceof SuperFunctionInvokation) return true;
        if (!(e instanceof MemberFunctionInvokation)) return false;
        return ((MemberFunctionInvokation) e).isInitMethod();
    }
}
