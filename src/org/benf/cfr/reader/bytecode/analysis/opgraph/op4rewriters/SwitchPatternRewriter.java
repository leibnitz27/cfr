package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.BytecodeMeta;
import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredCaseDefinitionExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredCaseUnassignedExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ClassFileVersion;
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
    private static Literal typeSwitchLabel = new Literal(TypedLiteral.getString("\"typeSwitch\""));

    public SwitchPatternRewriter(Options options, ClassFileVersion classFileVersion, BytecodeMeta bytecodeMeta) {
        this.options = options;
        this.classFileVersion = classFileVersion;
        this.bytecodeMeta = bytecodeMeta;
    }

    static class Gathered {
        private final StructuredCase cas;
        public Op04StructuredStatement definitionAssignment;
        public Op04StructuredStatement testContainer;
        public LValue definitionLvalue;
        public ConditionalExpression test;
        public boolean underscore;
        List<Integer> cases;
        List<Expression> types;

        public Gathered(StructuredCase cas) {
            this.cas = cas;
            cases = ListFactory.newList();
            types = ListFactory.newList();
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
        if (!name.equals(typeSwitchLabel)) return;
        Expression objects = args.get(1);
        if (!(objects instanceof NewAnonymousArray)) return;
        Expression originalSwitchValue = args.get(2);
        // use CastExpression.tryRemoveCast
        Expression actualSwitchValue = originalSwitchValue instanceof CastExpression ? ((CastExpression) originalSwitchValue).getChild() : originalSwitchValue;

        Expression originalSearchControlValue = args.get(3);
        // use CastExpression.tryRemoveCast
        originalSearchControlValue = originalSearchControlValue instanceof CastExpression ? ((CastExpression) originalSearchControlValue).getChild() : originalSearchControlValue;
        LValue actualSearchControlValue = originalSearchControlValue instanceof LValueExpression ? ((LValueExpression) originalSearchControlValue).getLValue() : null;

        NewAnonymousArray aargs = (NewAnonymousArray)objects;
        if (aargs.getNumDims() != 1) return;
        List<Expression> types = aargs.getValues();
        StructuredStatement body = swatch.getBody().getStatement();
        if (!(body instanceof Block)) return;
        Block block = (Block) body;
        List<Op04StructuredStatement> branches = block.getBlockStatements();
        // Collect the case statements.  We expect #types statements.
        Map<Integer, StructuredCase> cases = MapFactory.newMap();
        StructuredCase defalt = null;
        StructuredCase nul = null;
        for (Op04StructuredStatement branch : branches) {
            StructuredStatement s = branch.getStatement();
            if (!(s instanceof StructuredCase)) return;
            StructuredCase cays = (StructuredCase) s;
            List<Expression> values = cays.getValues();
            for (Expression value : values) {
                Integer i = getValue(value);
                if (i == null) return;
                if (i == -1) {
                    nul = cays;
                    continue;
                }
                if (i >= types.size()) return;
                cases.put(i, cays);
            }
            if (values.isEmpty()) {
                defalt = cays;
            }
        }
        // If there's a size mismatch, pad it out with 'default' for the missing branch.
        // BUT - a default can legitimately exist.
        if (types.size() == cases.size() + 1) {
            if (defalt != null) {
                // Other than -1, a value won't be assigned.
                for (int x=0;x<types.size();++x) {
                    if (!cases.containsKey(x)) {
                        cases.put(x, defalt);
                        defalt = null;
                        break;
                    }
                }
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

         */

        Map<StructuredCase, Gathered> rev = MapFactory.newLazyMap(new UnaryFunction<StructuredCase, Gathered>() {
            @Override
            public Gathered invoke(StructuredCase s) {
                return new Gathered(s);
            }
        });

        for (Map.Entry<Integer, StructuredCase> kv : cases.entrySet()) {
            Integer key = kv.getKey();
            Gathered gathered = rev.get(kv.getValue());
            gathered.cases.add(key);
            gathered.types.add(types.get(key));
        }

        List<StructuredContinue> controlSources = ListFactory.newList();
        for (Map.Entry<StructuredCase, Gathered> kv : rev.entrySet()) {
            StructuredCase k = kv.getKey();
            Gathered gathered = kv.getValue();
            Op04StructuredStatement caseBody = k.getBody();
            StructuredStatement stm = caseBody.getStatement();
            if (!(stm instanceof Block)) return;
            Block blk = (Block)stm;
            List<Op04StructuredStatement> blkstm = blk.getBlockStatements();
            if (blkstm.isEmpty()) return;
            Op04StructuredStatement defn = blkstm.get(0);
            Op04StructuredStatement pred = blkstm.size() > 1 ? blkstm.get(1) : null;
            StructuredStatement sdefn = defn.getStatement();

            /* There's two possibilities here - sdefn could be a cast,

               case Integer.class: {
                    Integer i = n3;
                    if (i <= n2) {

               or it could be a set of instanceofs.

               if (!(n3 instanceof Foo) && !(n3 instanceof Bar) ...... )



             */

            if (sdefn instanceof StructuredAssignment) {
                StructuredAssignment sdef =(StructuredAssignment) sdefn;
                LValue lv = sdef.getLvalue();
                if (sdef.isCreator(lv)) {
                    Expression rhs = sdef.getRvalue();
                    if (rhs instanceof CastExpression) {
                        // Bust through cast no matter what.
                        rhs = ((CastExpression) rhs).getChild();
                    }
                    if (rhs.equals(actualSwitchValue)) {
                        gathered.definitionLvalue = lv;
                        gathered.definitionAssignment = defn;
                    }
                }

                // If we've found an assignment, we might have found a when clause too
                if (gathered.definitionLvalue != null /*  || gathered.anonymous != null */) {
                    if (pred != null && pred.getStatement() instanceof StructuredIf) {
                        StructuredIf sif = (StructuredIf)pred.getStatement();
                        ConditionalExpression test = sif.getConditionalExpression();
                        // If it passes, we expect a body like :
                        // nextTest = 3
                        // continue block5
                        if (!extractGuards(sif, actualSearchControlValue, controlSources)) continue;
                        gathered.test = test.getNegated().simplify();
                        gathered.testContainer = pred;
                    }
                }

            } else if (sdefn instanceof StructuredIf) {
                StructuredIf sif = (StructuredIf)sdefn;
                ConditionalExpression ce = sif.getConditionalExpression();
                ce = ce.simplify();
                if (!(ce instanceof NotOperation)) {
                    ce = ce.getDemorganApplied(false);
                }
                // We expect a disjunction inside not.
                if (!(ce instanceof NotOperation)) {
                    return;
                }
                ce = ce.getNegated().getRightDeep(); // it was a not, so strip that.
                // At this point, it SHOULD be a right deep tree in DNF.
                if (!extractUnderscores(ce, actualSwitchValue)) return;
                gathered.underscore = true;
                gathered.definitionAssignment = sdefn.getContainer();
                if (!extractGuards(sif, actualSearchControlValue, controlSources)) return;
            } else {
                // This doesn't match known patterns.  Fail.
                return;
            }
        }

        // At this point, we've found all the gathered, we've found (hopefully) all the continues.
        // Check all controlSources continue to the same block, which covers the switch.

        // For that block, check that all sources are covered except the loop itself.
        BlockIdentifier controlBlock = null;
        if (!controlSources.isEmpty()) {
            for (StructuredContinue control : controlSources) {
                BlockIdentifier tgt = control.getContinueTgt();
                if (controlBlock == null) {
                    controlBlock = tgt;
                } else if (!controlBlock.equals(tgt)) {
                    controlBlock = null;
                    break;
                }
            }
        }

        Op04StructuredStatement controlLoopContainer = null;
        // There's only a control block if we have when statements.
        if (controlBlock != null) {
            List<Op04StructuredStatement> sources = switchStatement.getContainer().getSources();
            if (sources.size() == 1) {
                // These are the sources of the loop.
                sources = sources.get(0).getSources();
                sources = ListFactory.newList(sources);
                for (StructuredContinue control : controlSources) {
                    sources.remove(control.getContainer());
                }
                // Removed all the continues.... there should be one source left, which is the block controlBlock!
                if (sources.size() == 1) {
                    Op04StructuredStatement source = sources.get(0);
                    if (source.getStatement() instanceof StructuredWhile) {
                        StructuredWhile controlLoop = (StructuredWhile) source.getStatement();
                        if (controlBlock.equals(controlLoop.getBlock())) {
                            // YAY!
                            controlLoopContainer = source;
                        }
                    }
                }
            }
        }

        // Ok - we now know everything we need to rebuild!
        // At this point we can abort if we don't have enough info.
        
        for (Gathered g : rev.values()) {
            StructuredCase cas = g.cas;
            if (g.definitionAssignment != null) {
                g.definitionAssignment.nopOut();
            }
            cas.getValues().clear();
            for (Expression e : g.types) {
                if (e.getInferredJavaType().getJavaTypeInstance().getDeGenerifiedType().equals(TypeConstants.CLASS)) {
                    TypedLiteral lit = ((Literal)e).getValue();
                    JavaTypeInstance typ = lit.getClassValue();

                    if (g.underscore) {
                        cas.getValues().add(new StructuredCaseUnassignedExpression(new InferredJavaType(typ, InferredJavaType.Source.LITERAL)));
                    } else {
                        StructuredDefinition d = g.definitionLvalue == null ? null : new StructuredDefinition(g.definitionLvalue);
                        cas.getValues().add(new StructuredCaseDefinitionExpression(new InferredJavaType(typ, InferredJavaType.Source.LITERAL), d, g.test));
                        if (g.testContainer != null) {
                            g.testContainer.nopOut();
                        }
                    }
                } else {
                    cas.getValues().add(e);
                }
            }
        }


        // if defalt is still set, it's a legit default.
        if (nul != null) {
            nul.getValues().clear();
            nul.getValues().add(new Literal(TypedLiteral.getNull()));
        }

        Op04StructuredStatement resultContainer = switchStatement.getContainer();
        BlockIdentifier resultBlock = swatch.getBlockIdentifier();
        if (controlLoopContainer != null) {
            resultContainer = controlLoopContainer;
            resultBlock = controlBlock;
        }

        resultContainer.replaceStatement(new StructuredSwitch(
                BytecodeLoc.TODO,
                originalSwitchValue,
                swatch.getBody(),
                resultBlock, false));
    }

    private static boolean extractGuards(StructuredIf sif, LValue actualSearchControlValue, List<StructuredContinue> controlSources) {
        List<StructuredStatement> predbody = ListFactory.newList();
        // Should be done with a matcher.
        sif.getIfTaken().linearizeStatementsInto(predbody);
        if (predbody.size() == 4) {
            StructuredStatement next = predbody.get(1);
            StructuredStatement continu = predbody.get(2);
            if (!(next instanceof StructuredAssignment)) return false;
            if (!(continu instanceof StructuredContinue)) return false;
            StructuredAssignment sa = (StructuredAssignment) next;
            if (!sa.getLvalue().equals(actualSearchControlValue)) return false;
            controlSources.add((StructuredContinue)continu);
            return true;
        }
        return false;
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
