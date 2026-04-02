package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.BytecodeMeta;
import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.LocalUsageCheck;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Pattern;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.pattern.RecordPattern;
import org.benf.cfr.reader.bytecode.analysis.parse.pattern.TypePattern;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredCaseDefinitionExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredCaseUnassignedExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;

import java.util.List;
import java.util.Map;

public class SwitchPatternRewriter  implements Op04Rewriter {
    private final Options options;
    private final ClassFileVersion classFileVersion;
    private final BytecodeMeta bytecodeMeta;
    private final DCCommonState dcCommonState;
    private static Literal typeSwitchLabel = new Literal(TypedLiteral.getString("\"typeSwitch\""));
    private static Literal enumSwitchLabel = new Literal(TypedLiteral.getString("\"enumSwitch\""));

    public SwitchPatternRewriter(Options options, ClassFileVersion classFileVersion, BytecodeMeta bytecodeMeta, DCCommonState dcCommonState) {
        this.options = options;
        this.classFileVersion = classFileVersion;
        this.bytecodeMeta = bytecodeMeta;
        this.dcCommonState = dcCommonState;
    }

    enum GatheredType {
        None,
        Other, // literal, or underscore.
        Variable,
        Record
    };

    static class Gathered {
        private final StructuredCase cas;
        public Op04StructuredStatement definitionAssignment;
        public List<Op04StructuredStatement> additionalNops;
        public LValue definitionLvalue;
        public ConditionalExpression test;
        public boolean underscore;
        public Map<MemberFunctionInvokation, LValue> recordKeys;
        public List<Op04StructuredStatement> blkstm;
        // will be one of blkstm.
        public Op04StructuredStatement flattenConditional;
        // Potentially, locals that we can discard once we've done our
        // transform.... but maybe not!
        public List<Op04StructuredStatement> predeclarationNops;
        List<Integer> cases;
        List<Expression> replacements;
        GatheredType type = GatheredType.None;

        public Gathered(StructuredCase cas) {
            this.cas = cas;
            cases = ListFactory.newList();
            replacements = ListFactory.newList();
            additionalNops = ListFactory.newList();
        }
    }

    // Holds the result of collecting case branches from a typeSwitch.
    static class CaseCollection {
        final Map<Integer, StructuredCase> cases;
        StructuredCase defalt;
        StructuredCase nul;

        CaseCollection(Map<Integer, StructuredCase> cases, StructuredCase defalt, StructuredCase nul) {
            this.cases = cases;
            this.defalt = defalt;
            this.nul = nul;
        }
    }

    boolean extractUnderscores(ConditionalExpression ce, Expression switchValue) {
        ConditionalExpression lhs = (ce instanceof BooleanOperation) ? ((BooleanOperation) ce).getLhs() : ce;

        if (!(lhs instanceof BooleanExpression)) return false;
        Expression be = ((BooleanExpression) lhs).getInner();
        if (!(be instanceof InstanceOfExpression)) return false;

        if (!((InstanceOfExpression) be).getLhs().equals(switchValue)) return false;
        // We don't actually (this is lazy and could be tricked) check that these match the types in the case.
        // it's not actually clear why this is here at all, it should be impossible to get here if we don't match
        // expected types.

        if (ce instanceof BooleanOperation) return extractUnderscores(((BooleanOperation) ce).getRhs(), switchValue);
        return true;
    }

    void processOneSwatch(StructuredStatement switchStatement) {
        StructuredSwitch swatch = (StructuredSwitch) switchStatement;
        Expression on = swatch.getSwitchOn();
        if (!(on instanceof StaticFunctionInvokation)) return;
        StaticFunctionInvokation son = (StaticFunctionInvokation)on;
        if (!son.getClazz().getRawName().equals(TypeConstants.switchBootstrapsName)) return;
        List<Expression> args = son.getArgs();
        // Switchbootstraps arg 1 is types/values, arg 2 is switch values, arg 3 is "search from this index".
        if (args.size() != 4) return;
        Expression name = args.get(0);

        Expression objects = args.get(1);
        if (!(objects instanceof NewAnonymousArray)) return;
        Expression originalSwitchValue = args.get(2);
        // use CastExpression.tryRemoveCast
        Expression actualSwitchValue = originalSwitchValue instanceof CastExpression ? ((CastExpression) originalSwitchValue).getChild() : originalSwitchValue;
        NewAnonymousArray aargs = (NewAnonymousArray) objects;
        if (aargs.getNumDims() != 1) return;
        List<Expression> argList = aargs.getValues();

        if (name.equals(typeSwitchLabel)) {
            typeSwitch(switchStatement, args, swatch, argList, actualSwitchValue, originalSwitchValue);
        } else if (name.equals(enumSwitchLabel)) {
            enumSwitch(switchStatement, args, swatch, argList, actualSwitchValue, originalSwitchValue);
        }
    }

    private void enumSwitch(StructuredStatement switchStatement, List<Expression> args, StructuredSwitch swatch, List<Expression> argList, Expression actualSwitchValue, Expression originalSwitchValue) {
        // Construct static variable references from the string names in the bootstrap args.
        // We could look these up via the enum class, but the string-based approach works even
        // when the enum class can't be loaded.
        for (int x=0;x<argList.size();++x) {
            Expression arg = argList.get(x);
            if (arg instanceof Literal) {
                TypedLiteral tl = ((Literal) arg).getValue();
                if (tl.getType() == TypedLiteral.LiteralType.String) {
                    String name = (String)tl.getValue();
                    if (name.startsWith("\"") && name.endsWith("\"")) {
                        name = name.substring(1,name.length()-1);
                    }
                    StaticVariable sv = new StaticVariable(originalSwitchValue.getInferredJavaType(), originalSwitchValue.getInferredJavaType().getJavaTypeInstance(), name);
                    argList.set(x, new LValueExpression(sv));
                }
            }
        }

        typeSwitch(switchStatement, args, swatch, argList, actualSwitchValue, originalSwitchValue);
    }

    // We are a multiple Foo _, Bar _, Bap _.
    // This is going to become much more painful if those are ever allowed to become nonignored!
    private boolean identifyMultiCase(List<Op04StructuredStatement> blkstm, Gathered gathered, Expression actualSwitchValue, LValue actualSearchControlValue, List<StructuredContinue> controlSources) {
        StructuredStatement sdefn = blkstm.get(0).getStatement();
        if (sdefn instanceof StructuredIf) {
            StructuredIf sif = (StructuredIf) sdefn;
            ConditionalExpression ce = sif.getConditionalExpression();
            ce = ce.simplify();
            if (!(ce instanceof NotOperation)) {
                ce = ce.getDemorganApplied(false);
            }
            // We expect a disjunction inside not.
            if (!(ce instanceof NotOperation)) {
                return false;
            }
            ce = ce.getNegated().getRightDeep(); // it was a not, so strip that.
            // At this point, it SHOULD be a right deep tree in DNF.
            if (!extractUnderscores(ce, actualSwitchValue)) return false;
            // guards not possible for a multi case.
            gathered.underscore = true;
            gathered.definitionAssignment = sdefn.getContainer();

            // Need to collect control source
            if (!extractGuards(gathered, sif, actualSearchControlValue, controlSources)) return false;
            return true;
        }
        return false;
    }

    private boolean extractTypeswitchCaseBody(StructuredCase k, Gathered gathered, Expression actualSwitchValue, LValue actualSearchControlValue, List<StructuredContinue> controlSources) {
        Op04StructuredStatement caseBody = k.getBody();
        StructuredStatement stm = caseBody.getStatement();
        if (!(stm instanceof Block)) return false;
        Block blk = (Block) stm;
        List<Op04StructuredStatement> blkstm = blk.getBlockStatements();
        if (blkstm.isEmpty()) return false;
        gathered.blkstm = blkstm;
        int contentidx = 0;

            /* There's two possibilities here - sdefn could be a cast,

               case Integer.class: {
                    Integer i = n3; <--- a lot more complex with records.
                    if (i <= n2) {

               or it could be a set of instanceofs.

               if (!(n3 instanceof Foo) && !(n3 instanceof Bar) ...... )

             */
        if (identifyMultiCase(blkstm, gathered, actualSwitchValue, actualSearchControlValue, controlSources)) {
            gathered.type = GatheredType.Other;
            return true;
        }

        // Can we collapse the next n statements down into a match destructure?
        Integer nextIdx = extractStructuredLocal(blkstm, contentidx, gathered, actualSwitchValue);
        if (nextIdx == null) {
            return false;
        }
        contentidx = nextIdx;

        // If we've found an assignment, we might have found a when clause too
        // TODO : This could be negated!!
        /*
                if (y < a) {
                    String string = "point3(" + y + "," + a + ")";
                    return string;
                }
                n = 1;
                continue block5;
         */
        if (gathered.definitionLvalue != null) {
            Op04StructuredStatement pred = blkstm.size() > contentidx + 1 ? blkstm.get(contentidx) : null;
            if (pred != null && pred.getStatement() instanceof StructuredIf) {
                StructuredIf sif = (StructuredIf) pred.getStatement();
                // If it passes, we expect a body like :
                // nextTest = 3
                // continue block5
                if (!extractGuards(gathered, sif, actualSearchControlValue, controlSources)) return true;
            }
        }
        // TODO : If we've not gathered anything useful, why not fail?
        // TODO : Are these paths exclusive?
        return true;
    }

    /*
    a lot of temporaries get generated :(
    This is a real reflection of the bytecode, likely an empty guard.

        42: invokevirtual #13                 // Method org/benf/cfr/tests/PatternSwitch15e$Point.x:()I
        45: istore        6   //
        47: iload         6   //
        49: istore        7   //
        51: iconst_1          // Empty guard
        52: ifeq          84
        55: iload         6
        57: istore        4
        59: aload_3
         from    to  target type
            42    45   107   Class java/lang/Throwable

                    int n2;
                    Point point = (Point)((Object)object);
                    int n3 = n2 = point.x();
                    int y = n2;
                    n3 = n2 = point.y();
                    int a = n2;
     */
    private Integer extractStructuredLocal(List<Op04StructuredStatement> blkstm, int contentidx, Gathered gathered, Expression actualSwitchValue) {
        if (gathered.replacements.size() != 1) {
            return null; // multitest
        }
        Expression replacement = gathered.replacements.get(0);
        if (replacement instanceof LValueExpression) {
            gathered.type = GatheredType.Other;
            return contentidx;
        }
        if (!(replacement instanceof Literal)) {
            return null;
        }
        TypedLiteral tl = ((Literal) replacement).getValue();
        JavaTypeInstance replacementType = null;
        if (tl.getType() == TypedLiteral.LiteralType.Class) {
            replacementType = tl.getClassValue();
        } else {
            // Not quite true.
            gathered.type = GatheredType.Other;
            return contentidx;
        }

        LValue defnAssignement = null;
        // skip over, but collect declarations.
        // We can't really tell the difference between PatternSwitch15f and PatternSwitch15g, however
        // in the case of the inline destructuring, there's an unnecessary local.
        //
        // We rely VERY heavily on pattern of assignments.
        //

        List<Op04StructuredStatement> predeclarationNops = ListFactory.newList();
        List<Op04StructuredStatement> flushableNops = ListFactory.newList();
        // TODO: This could (if we've not stripped out of desperation) be in a matchException block.
        Map<LValue, MemberFunctionInvokation> destructure = MapFactory.newOrderedMap();
        Map<LValue, Op04StructuredStatement> defined = MapFactory.newMap();
        for (;contentidx<blkstm.size();++contentidx) {
            Op04StructuredStatement stm = blkstm.get(contentidx);
            StructuredStatement s = stm.getStatement();
            if (s instanceof StructuredComment) {
                if (defnAssignement == null) {
                    gathered.additionalNops.add(stm);
                } else {
                    flushableNops.add(stm);
                }
                continue;
            }
            // Any definition or assignment we find after the first could be part of the
            // case body - we can't always roll them up into a pattern match....
            // We can't change gathered, if we've found a valid Variable.

            if (s instanceof StructuredDefinition) {
                StructuredDefinition sd = (StructuredDefinition)s;
                defined.put(sd.getLvalue(), stm);
                if (defnAssignement == null) {
                    predeclarationNops.add(stm);
                } else {
                    flushableNops.add(stm);
                }
                continue;
            }
            if (s instanceof StructuredAssignment) {
                StructuredAssignment sa = (StructuredAssignment)s;
                if (defnAssignement == null) {
                    LValue lv = sa.getLvalue();
                    if (!sa.isCreator(lv)) {
                        // It shouldn't be possible NOT to be the creator, if we're using a pattern
                        // match.
                        // In SwitchPatternRegression3, the variable which is being assigned to is already
                        // in the outer scope. (which is not correct, but likely due to mislabelling of lifetimes).
                        // This should be vanishingly rare, so we'll introduce a temporary.
                        LValue tmp = new LocalVariable("cfr_tmp", lv.getInferredJavaType());
                        StructuredAssignment sa2 = new StructuredAssignment(sa.getCombinedLoc(), tmp, sa.getRvalue(), true);
                        stm.replaceStatement(sa2);
                        StructuredAssignment ae = new StructuredAssignment(sa.getCombinedLoc(), lv, new LValueExpression(tmp));
                        Op04StructuredStatement ins = new Op04StructuredStatement(stm.getIndex().justAfter(), stm.getBlockMembership(), ae);
                        Op04StructuredStatement prevT = stm.getTargets().get(0);
                        stm.replaceTarget(prevT, ins);
                        prevT.replaceSource(stm, ins);
                        ins.addSource(stm);
                        ins.addTarget(prevT);
                        blkstm.add(contentidx, ins);
                        lv = tmp;
                    }
                    JavaTypeInstance sat = sa.getLvalue().getInferredJavaType().getJavaTypeInstance();
                    if (!sat.equals(replacementType)) {
                        return null;
                    }
                    Expression rhs = sa.getRvalue();
                    if (rhs instanceof CastExpression) {
                        // Bust through cast no matter what.
                        rhs = ((CastExpression) rhs).getChild();
                    }
                    if (rhs.equals(actualSwitchValue)) {
                        gathered.definitionLvalue = lv;
                        gathered.definitionAssignment = stm;
                    } else {
                        return null;
                    }
                    defnAssignement = lv;
                    // Don't need.
                    gathered.additionalNops.add(stm);
                    continue;
                } else {
                    BindingSuperContainer bc = defnAssignement.getInferredJavaType().getJavaTypeInstance().getBindingSupers();
                    if (bc == null || !bc.containsBase(TypeConstants.RECORD)) {
                        break;
                    }
                    if (!extractDestructuringChain(sa, defnAssignement, destructure, defined, flushableNops, stm)) {
                        break;
                    }
                    continue;
                }
            }
            break;
        }

        if (gathered.definitionLvalue == null) {
            return null;
        }

        /*
         * Ensure all lvalues used in destructure are defined.  Remove all entries from destructure that aren't
         * used in subsequent statements (need to check recursively).
         *
         * Note, subsequent to this, we can (and likely will!) have when blocks.
         */
        gathered.predeclarationNops = predeclarationNops;
        if (destructure.isEmpty()) {
            gathered.type = GatheredType.Variable;
            return contentidx;
        }
        gathered.additionalNops.addAll(flushableNops);

        Map<MemberFunctionInvokation, LValue> usedFuncs = findUsedDestructuredFields(blkstm, contentidx, destructure, defined);
        if (usedFuncs == null) {
            return null;
        }

        gathered.recordKeys = usedFuncs;
        gathered.type = GatheredType.Record;
        return contentidx;
    }

    // Walk a chain of assignments after the initial definition, looking for record component
    // accessor invocations (e.g. point.x(), point.y()).  Returns true if the chain was valid
    // (even if empty), false to break out of the loop.
    private static boolean extractDestructuringChain(StructuredAssignment sa, LValue defnAssignement,
            Map<LValue, MemberFunctionInvokation> destructure, Map<LValue, Op04StructuredStatement> defined,
            List<Op04StructuredStatement> flushableNops, Op04StructuredStatement stm) {
        List<LValue> lvs = ListFactory.newList();
        // We expect this to be a chain of assignments that tracks to a property of the record.
        LValue lhs = sa.getLvalue();
        Expression rhs = sa.getRvalue();

        do {
            if (sa.isCreator(lhs)) {
                defined.put(lhs, stm);
            }
            lvs.add(lhs);
            if (rhs instanceof AssignmentExpression) {
                lhs = ((AssignmentExpression) rhs).getlValue();
                rhs = ((AssignmentExpression) rhs).getrValue();
            } else {
                break;
            }
        } while (true);
        if (rhs instanceof LValueExpression) {
            MemberFunctionInvokation tmp = destructure.get(((LValueExpression) rhs).getLValue());
            if (tmp != null) rhs = tmp;
        }
        // If we're calling a member, are we destructuring a record? Or are we just calling a plausible looking function
        // consider SwitchPatternRegression2.
        if (rhs instanceof MemberFunctionInvokation) {
            MemberFunctionInvokation mf = (MemberFunctionInvokation)rhs;
            Expression mfe = mf.getObject();
            if (!(mfe instanceof LValueExpression)) return false;
            LValue mfl = ((LValueExpression) mfe).getLValue();
            if (!mfl.equals(defnAssignement)) return false;
            // Is it an invocation on our extracted object?
            for (LValue lv : lvs) {
                destructure.put(lv, mf);
            }
            flushableNops.add(stm);
            return true;
        }
        return false;
    }

    // Determine which destructured fields are actually referenced in subsequent statements.
    // Returns the used fields map, or null if there's a conflict (same accessor used twice).
    private static Map<MemberFunctionInvokation, LValue> findUsedDestructuredFields(
            List<Op04StructuredStatement> blkstm, int contentidx,
            Map<LValue, MemberFunctionInvokation> destructure,
            Map<LValue, Op04StructuredStatement> defined) {
        final UsageCheck uc = new UsageCheck(defined);
        StructuredStatementTransformer usagecheck = new StructuredStatementTransformer() {
            @Override
            public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
                in.rewriteExpressions(uc);
                return in;
            }
        };

        for (int remain = contentidx; remain < blkstm.size(); ++remain) {
            blkstm.get(remain).transform(usagecheck, new StructuredScope());
        }

        /* There should be at most one distinct usage for each of the properties of the record
         * - it may be that one of the usages isn't used, in which case while it may have a name
         * (meaning we can probably guess that it was), it may be better to treat as anonymous.
         */
        Map<MemberFunctionInvokation, LValue> usedFuncs = MapFactory.newOrderedMap();
        for (Map.Entry<LValue, MemberFunctionInvokation> destr : destructure.entrySet()) {
            LValue lv = destr.getKey();
            if (uc.used.containsKey(lv)) {
                MemberFunctionInvokation mfi = destr.getValue();
                if (usedFuncs.containsKey(mfi)) {
                    return null;
                }
                usedFuncs.put(mfi, lv);
            }
        }
        return usedFuncs;
    }

    static class UsageCheck extends AbstractExpressionRewriter {
        private final Map<LValue, Op04StructuredStatement> checks;
        public Map<LValue, Boolean> used = MapFactory.newMap();

        UsageCheck(Map<LValue, Op04StructuredStatement> checks) {
            this.checks = checks;
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (checks.containsKey(lValue)) {
                used.put(lValue, true);
            }
            return super.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
        }
    }

    /*
       Conditions are implemented by (!!) having a loop around the switch statement, and
       repeatedly calling switchBootstraps, with a higher 4th arg to preclude earlier cases.
       We expect something like

       block5 : while (true) {
         switch (n3) {
           case Integer.class: {
                Integer i = n3;
                if (i <= n2) {
                    n4 = 2;
                    continue block5;
                }
                res = "bigger than n";
                break block5;
            }

       This becomes:

       block5 : while (true) {
         switch (n3) {
           case Integer i : {
                if (i <= n2) {
                    n4 = 2;
                    continue block5;
                }
                res = "bigger than n";
                break block5;
            }

       This becomes:

       block5 : while (true) {
         switch (n3) {
          case Integer i when i > n2:
            res = "bigger than n";
            break block5;

       We then eliminate the loop block5, and it becomes

       switch (n3) {
         case Integer i when i > n2:
          res = "bigger than n";
          break;

        Note - for record destructuring in place, this becomes significantly more complex.

     */
    private void typeSwitch(StructuredStatement switchStatement, List<Expression> args, StructuredSwitch swatch, List<Expression> argList, Expression actualSwitchValue, Expression originalSwitchValue) {
        Expression originalSearchControlValue = args.get(3);
        // use CastExpression.tryRemoveCast
        originalSearchControlValue = originalSearchControlValue instanceof CastExpression ? ((CastExpression) originalSearchControlValue).getChild() : originalSearchControlValue;
        LValue actualSearchControlValue = originalSearchControlValue instanceof LValueExpression ? ((LValueExpression) originalSearchControlValue).getLValue() : null;

        // Collect the case branches from the switch.
        CaseCollection cc = collectSwitchCases(swatch, argList);
        if (cc == null) return;

        // For each case, gather the definition/destructure/guard info.
        Map<StructuredCase, Gathered> rev = MapFactory.newLazyMap(new UnaryFunction<StructuredCase, Gathered>() {
            @Override
            public Gathered invoke(StructuredCase s) {
                return new Gathered(s);
            }
        });

        for (Map.Entry<Integer, StructuredCase> kv : cc.cases.entrySet()) {
            Integer key = kv.getKey();
            Gathered gathered = rev.get(kv.getValue());
            gathered.cases.add(key);
            gathered.replacements.add(argList.get(key));
        }

        List<StructuredContinue> controlSources = ListFactory.newList();
        for (Map.Entry<StructuredCase, Gathered> kv : rev.entrySet()) {
            if (!extractTypeswitchCaseBody(kv.getKey(), kv.getValue(), actualSwitchValue, actualSearchControlValue, controlSources)) {
                return;
            };
            if (kv.getValue().type == GatheredType.None) {
                return;
            }
        }

        // Find the control loop (if when-guards are present).
        BlockIdentifier controlBlock = findControlBlock(controlSources);
        int controlRefCnt = controlSources.size();
        Op04StructuredStatement controlLoopContainer = null;
        if (controlBlock != null) {
            controlLoopContainer = findControlLoop(switchStatement, controlSources, controlBlock);
        }

        // Apply the gathered results - rebuild case values.
        applyGatheredResults(rev, cc.nul);

        // Rebuild the switch statement.
        Op04StructuredStatement resultContainer = switchStatement.getContainer();
        BlockIdentifier resultBlock = swatch.getBlockIdentifier();
        if (controlLoopContainer != null) {
            resultContainer = controlLoopContainer;
            resultBlock = controlBlock;
            controlBlock.releaseForeignRefs(controlRefCnt);
        }

        resultContainer.replaceStatement(new StructuredSwitch(
                BytecodeLoc.TODO,
                originalSwitchValue,
                swatch.getBody(),
                resultBlock, false));
    }

    // Parse the switch branches into a map of index->case, plus default and null cases.
    // Returns null if the branches can't be parsed.
    private CaseCollection collectSwitchCases(StructuredSwitch swatch, List<Expression> argList) {
        StructuredStatement body = swatch.getBody().getStatement();
        if (!(body instanceof Block)) return null;
        Block block = (Block) body;
        List<Op04StructuredStatement> branches = block.getBlockStatements();

        Map<Integer, StructuredCase> cases = MapFactory.newMap();
        StructuredCase defalt = null;
        StructuredCase nul = null;
        for (Op04StructuredStatement branch : branches) {
            StructuredStatement s = branch.getStatement();
            if (!(s instanceof StructuredCase)) return null;
            StructuredCase cays = (StructuredCase) s;
            if (cays.isDefault()) {
                defalt = cays;
            } else {
                List<Expression> values = cays.getValues();
                for (Expression value : values) {
                    Integer i = getValue(value);
                    if (i == null) return null;
                    if (i == -1) {
                        nul = cays;
                        continue;
                    }
                    if (i >= argList.size()) return null;
                    cases.put(i, cays);
                }
            }
        }

        // If no branch for 'case null' exists, add one because `SwitchBootstraps` allows null values;
        // otherwise the dumped code would erroneously fail with a NullPointerException
        if (nul == null && defalt != null) {
            defalt.markHandlesNull();
        }

        // If there's a size mismatch, pad it out with 'default' for the missing branch.
        // BUT - a default can legitimately exist.
        if (argList.size() == cases.size() + 1) {
            if (defalt != null) {
                // Other than -1, a value won't be assigned.
                for (int x = 0; x < argList.size(); ++x) {
                    if (!cases.containsKey(x)) {
                        cases.put(x, defalt);
                        defalt = null;
                        break;
                    }
                }
            }
        }
        return new CaseCollection(cases, defalt, nul);
    }

    // Find the single block that all control continues target, or null if they disagree.
    private static BlockIdentifier findControlBlock(List<StructuredContinue> controlSources) {
        if (controlSources.isEmpty()) return null;
        BlockIdentifier controlBlock = null;
        for (StructuredContinue control : controlSources) {
            BlockIdentifier tgt = control.getContinueTgt();
            if (controlBlock == null) {
                controlBlock = tgt;
            } else if (!controlBlock.equals(tgt)) {
                return null;
            }
        }
        return controlBlock;
    }

    // Find the enclosing while loop that the control continues target.
    // Returns the while loop container, or null if not found.
    private static Op04StructuredStatement findControlLoop(StructuredStatement switchStatement,
            List<StructuredContinue> controlSources, BlockIdentifier controlBlock) {
        List<Op04StructuredStatement> sources = switchStatement.getContainer().getSources();
        if (sources.size() != 1) return null;
        // These are the sources of the loop.
        sources = sources.get(0).getSources();
        sources = ListFactory.newList(sources);
        for (StructuredContinue control : controlSources) {
            sources.remove(control.getContainer());
        }
        // Removed all the continues.... there should be one source left, which is the block controlBlock!
        if (sources.size() != 1) return null;
        Op04StructuredStatement source = sources.get(0);
        if (!(source.getStatement() instanceof StructuredWhile)) return null;
        StructuredWhile controlLoop = (StructuredWhile) source.getStatement();
        if (!controlBlock.equals(controlLoop.getBlock())) return null;
        return source;
    }

    // Apply the gathered analysis to each case: flatten conditionals, nop out consumed
    // statements, and replace integer case labels with pattern/type expressions.
    private void applyGatheredResults(Map<StructuredCase, Gathered> rev, StructuredCase nul) {
        for (Gathered g : rev.values()) {
            StructuredCase cas = g.cas;
            // The body of this conditional needs to be lifted into its parent.
            if (g.flattenConditional != null) {
                int ifPos = g.blkstm.indexOf(g.flattenConditional);
                StructuredIf sif = (StructuredIf)g.flattenConditional.getStatement();
                Op04StructuredStatement ifbody = sif.getIfTaken();
                if (!(ifbody.getStatement() instanceof Block)) {
                    // can't happen.
                    return;
                }
                List<Op04StructuredStatement> predbody = ((Block) ifbody.getStatement()).getBlockStatements();
                g.blkstm.addAll(ifPos, predbody);
            }

            if (g.definitionAssignment != null) {
                g.definitionAssignment.nopOut();
            }
            if (g.additionalNops != null) {
                for (Op04StructuredStatement nop : g.additionalNops) {
                    nop.nopOut();
                }
            }
            cas.getValues().clear();
            for (Expression e : g.replacements) {
                Expression caseValue = buildCaseValue(g, e);
                if (caseValue != null) {
                    cas.getValues().add(caseValue);
                }
            }
            // Now, examine the body that remains.
            // If predeclarationNops exist, we can probably delete them, if they're never *read*.
            // We count a single use as not read.
            // eg
            // local foo
            // a = foo = bar();
            // We could do this with a general purpose pointless local remover.
            if (g.predeclarationNops != null) {
                removePointlessVars(g.predeclarationNops, cas.getBody());
            }
        }

        // if defalt is still set, it's a legit default.
        if (nul != null) {
            nul.getValues().clear();
            nul.getValues().add(new Literal(TypedLiteral.getNull()));
        }
    }

    private void removePointlessVars(List<Op04StructuredStatement> predeclarationNops, Op04StructuredStatement body) {
        if (predeclarationNops.isEmpty()) return;
        List<LValue> lValues = Functional.map(predeclarationNops, new UnaryFunction<Op04StructuredStatement, LValue>() {
            @Override
            public LValue invoke(Op04StructuredStatement arg) {
                return ((StructuredDefinition)arg.getStatement()).getLvalue();
            }
        });
        LocalUsageCheck lc = new LocalUsageCheck(lValues);
        body.transform(lc, new StructuredScope());
        for (Op04StructuredStatement stm : predeclarationNops) {
            LValue lv =  ((StructuredDefinition)stm.getStatement()).getLvalue();
            int count = lc.usage(lv);
            if (count == 0) {
                stm.nopOut();
            } else if (count == 1) {
                if (lc.rewriteAway(lv)) {
                    stm.nopOut();
                }
            }
        }
    }

    // Build the replacement case value expression for a single gathered case entry.
    private Expression buildCaseValue(Gathered g, Expression e) {
        if (!e.getInferredJavaType().getJavaTypeInstance().getDeGenerifiedType().equals(TypeConstants.CLASS)) {
            return e;
        }
        TypedLiteral lit = ((Literal) e).getValue();
        JavaTypeInstance typ = lit.getClassValue();
        InferredJavaType ijt = new InferredJavaType(typ, InferredJavaType.Source.LITERAL);

        if (g.underscore || g.definitionLvalue == null) {
            return new StructuredCaseUnassignedExpression(ijt);
        } else if (g.type == GatheredType.Variable) {
            Pattern p = new TypePattern(g.definitionLvalue);
            return new StructuredCaseDefinitionExpression(ijt, p, g.test);
        } else if (g.type == GatheredType.Record) {
            // The only other place we dump a record like this is in the
            // ClassFileDumperRecord.
            Pattern p = createRecordPattern(g);
            if (p == null) {
                // We *could* replace this with a placeholder.
                return null;
            }
            return new StructuredCaseDefinitionExpression(ijt, p, g.test);
        }
        return null;
    }

    private Pattern createRecordPattern(Gathered g) {
        InferredJavaType ijt = g.definitionLvalue.getInferredJavaType();
        ClassFile c;
        try {
            c = this.dcCommonState.getClassFile(ijt.getJavaTypeInstance());
            // TODO: Common code ClassFileDumperRecord
        } catch (ConfusedCFRException ce) {
            // Ok - if we can't load it, can we guess? Should we?
            return null;
        }
        List<ClassFileField> fields = Functional.filter(c.getFields(), new Predicate<ClassFileField>() {
            @Override
            public boolean test(ClassFileField in) {
                return !in.getField().testAccessFlag(AccessFlag.ACC_STATIC);
            }
        });
        Map<MemberFunctionInvokation, LValue> assign = g.recordKeys;
        if (assign.size() != fields.size()) {
            return null;
        }
        Map<String, LValue> keyByName = MapFactory.newMap();
        for (Map.Entry<MemberFunctionInvokation, LValue> entry : assign.entrySet()) {
            keyByName.put(entry.getKey().getName(), entry.getValue());
        }
        List<LValue> lvs = ListFactory.newList();
        for (ClassFileField field : fields) {
            String name = field.getFieldName();
            LValue lv = keyByName.get(name);
            if (lv == null) {
                return null;
            }
            lvs.add(lv);
        }
        return new RecordPattern(g.definitionLvalue, lvs);
    }

    // Updates controlsources - must be success path.
    private static boolean extractGuards(Gathered gathered, StructuredIf sif, LValue actualSearchControlValue, List<StructuredContinue> controlSources) {
        /*
           We might get taken if fails the match, which looks like
               case Point(int y, int a): {
                    if (y < a) {
                      n = 1;
                      continue block5;
                    }.....
                }

           or , the assign and continue might be directly after the conditional.

           TODO :   Should be done with a matcher.
         */
        ConditionalExpression test = sif.getConditionalExpression();
        if (extractFailGuard(gathered, sif, actualSearchControlValue, controlSources)) {
            gathered.test = test.getNegated().simplify();
            return true;
        }
        if (extractSucceedGuard(gathered, sif, actualSearchControlValue, controlSources)) {
            gathered.test = test.simplify();
            return true;
        }
        return false;
    }

    private static boolean extractSucceedGuard(Gathered gathered, StructuredIf sif, LValue actualSearchControlValue, List<StructuredContinue> controlSources) {
        Op04StructuredStatement pred = sif.getContainer();
        List<Op04StructuredStatement> sifTargets = pred.getTargets();
        if (sifTargets.size() != 2) return false;
        Op04StructuredStatement nexts = sifTargets.get(1);
        StructuredStatement next = nexts.getStatement();
        if (!(next instanceof StructuredAssignment)) return false;
        StructuredAssignment sa = (StructuredAssignment) next;
        if (!sa.getLvalue().equals(actualSearchControlValue)) return false;
        if (nexts.getTargets().size() != 1) return false;
        Op04StructuredStatement continus = nexts.getTargets().get(0);
        StructuredStatement continu = continus.getStatement();
        if (!(continu instanceof StructuredContinue)) return false;
        controlSources.add((StructuredContinue)continu);

        gathered.flattenConditional = pred;
        gathered.additionalNops.add(pred);
        gathered.additionalNops.add(nexts);
        gathered.additionalNops.add(continus);
        return true;
    }

    private static boolean extractFailGuard(Gathered gathered, StructuredIf sif, LValue actualSearchControlValue, List<StructuredContinue> controlSources) {
        Op04StructuredStatement body = sif.getIfTaken();
        if (!(body.getStatement() instanceof Block)) return false;
        List<Op04StructuredStatement> predbody = ((Block) body.getStatement()).getBlockStatements();
        if (predbody.size() != 2) return false;
        StructuredStatement next = predbody.get(0).getStatement();
        if (!(next instanceof StructuredAssignment)) return false;
        StructuredAssignment sa = (StructuredAssignment) next;
        if (!sa.getLvalue().equals(actualSearchControlValue)) return false;
        StructuredStatement continu = predbody.get(1).getStatement();
        if (!(continu instanceof StructuredContinue)) return false;
        controlSources.add((StructuredContinue)continu);

        // We can just nop out the conditional.
        gathered.additionalNops.add(sif.getContainer());
        return true;
    }

    Integer getValue(Expression value) {
        if (!(value instanceof Literal)) return null;
        TypedLiteral tl = ((Literal) value).getValue();
        if (tl.getType() != TypedLiteral.LiteralType.Integer) return null;
        return tl.getIntValue();
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        List<StructuredStatement> switchStatements = Functional.filter(structuredStatements, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return in.getClass() == StructuredSwitch.class;
            }
        });

        if (switchStatements.isEmpty()) return;

        /*
         * Because the bodies of the case statements themselves are arbitrary, we can't use linearised matching here - need to improve that!
         */
        for (StructuredStatement switchStatement : switchStatements) {
            processOneSwatch(switchStatement);
        }
    }
}
