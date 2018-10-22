package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SwitchEnumRewriter implements Op04Rewriter {
    private final DCCommonState dcCommonState;
    private final ClassFileVersion classFileVersion;
    private final BlockIdentifierFactory blockIdentifierFactory;
    private final static JavaTypeInstance expectedLUTType = new JavaArrayTypeInstance(1, RawJavaType.INT);

    public SwitchEnumRewriter(DCCommonState dcCommonState, ClassFileVersion classFileVersion, BlockIdentifierFactory blockIdentifierFactory) {
        this.dcCommonState = dcCommonState;
        this.classFileVersion = classFileVersion;
        this.blockIdentifierFactory = blockIdentifierFactory;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        Options options = dcCommonState.getOptions();
        if (!options.getOption(OptionsImpl.ENUM_SWITCH, classFileVersion)) return;

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        List<StructuredStatement> switchStatements = Functional.filter(structuredStatements, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return in.getClass() == StructuredSwitch.class;
            }
        });
        WildcardMatch wcm = new WildcardMatch();

        if (!switchStatements.isEmpty()) {
            MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(switchStatements);

            Matcher<StructuredStatement> m = new ResetAfterTest(wcm,
                    new CollectMatch("switch", new StructuredSwitch(
                            new ArrayIndex(
                                    new LValueExpression(wcm.getLValueWildCard("lut")),
                                    wcm.getMemberFunction("fncall", "ordinal", wcm.getExpressionWildCard("object"))),
                            null, wcm.getBlockIdentifier("block"))));


            SwitchEnumMatchResultCollector matchResultCollector = new SwitchEnumMatchResultCollector();
            while (mi.hasNext()) {
                mi.advance();
                matchResultCollector.clear();
                if (m.match(mi, matchResultCollector)) {
                    tryRewrite(matchResultCollector, false);
                    mi.rewind1();
                }
            }
        }

        // We also have the vanishingly unlikely but quite silly case of switching on a literal with no content.
        // See switchTest23
        List<StructuredStatement> expressionStatements = Functional.filter(structuredStatements, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return in.getClass() == StructuredExpressionStatement.class;
                }
        });
        if (!expressionStatements.isEmpty()) {
            Matcher<StructuredStatement> mInline = new ResetAfterTest(wcm,
                    new CollectMatch("bodylessswitch", new StructuredExpressionStatement(
                            new ArrayIndex(
                                    new LValueExpression(wcm.getLValueWildCard("lut")),
                                    wcm.getMemberFunction("fncall", "ordinal", wcm.getExpressionWildCard("object"))),
                            true)));

            MatchIterator<StructuredStatement> mi2 = new MatchIterator<StructuredStatement>(expressionStatements);
            SwitchEnumMatchResultCollector matchResultCollector2 = new SwitchEnumMatchResultCollector();
            while (mi2.hasNext()) {
                mi2.advance();
                matchResultCollector2.clear();
                if (mInline.match(mi2, matchResultCollector2)) {
                    tryRewrite(matchResultCollector2, true);
                    mi2.rewind1();
                }
            }
        }
    }


    /*

     we expect the 'foreign class' to have a static initialiser which looks much like this.

     static void <clinit>()
{
    EnumSwitchTest1$1.$SwitchMap$org$benf$cfr$tests$EnumSwitchTest1$enm = new int[EnumSwitchTest1$enm.values().length];

    try {
        EnumSwitchTest1$1.$SwitchMap$org$benf$cfr$tests$EnumSwitchTest1$enm[EnumSwitchTest1$enm.ONE.ordinal()] = 1;
    }
    catch (NoSuchFieldError unnamed_local_ex_0) {
    }
    try {
        EnumSwitchTest1$1.$SwitchMap$org$benf$cfr$tests$EnumSwitchTest1$enm[EnumSwitchTest1$enm.TWO.ordinal()] = 2;
    }
    catch (NoSuchFieldError unnamed_local_ex_0) {
    }
}

     */
    private void tryRewrite(SwitchEnumMatchResultCollector mrc, boolean expression) {
        LValue lookupTable = mrc.getLookupTable();
        Expression enumObject = mrc.getEnumObject();

        if (!(lookupTable instanceof StaticVariable)) {
            return;
        }

        StaticVariable staticLookupTable = (StaticVariable) lookupTable;
        JavaTypeInstance classInfo = staticLookupTable.getOwningClassType();  // The inner class
        String varName = staticLookupTable.getFieldName();

        /*
         * All cases will of course be integers.  The lookup table /COULD/ be perverse, but that wouldn't
         * stop it being valid for this use.... as long as the array matches the indexes.
         *
         * So here's the tricky bit - we now have to load (cached?) clazz, and find out if
         * varName was initialised like a lookup table....
         */
        ClassFile enumLutClass;
        try {
            enumLutClass = dcCommonState.getClassFile(classInfo);
        } catch (CannotLoadClassException e) {
            // Oh dear, can't load that class.  Proceed without it.
            return;
        }
        Field lut;
        try {
            lut = enumLutClass.getFieldByName(varName, staticLookupTable.getInferredJavaType().getJavaTypeInstance()).getField();
        } catch (NoSuchFieldException e) {
            return;
        }
        JavaTypeInstance fieldType = lut.getJavaTypeInstance();
        if (!fieldType.equals(expectedLUTType)) return;

        Method lutStaticInit;
        try {
            lutStaticInit = enumLutClass.getMethodByName("<clinit>").get(0);
        } catch (NoSuchMethodException e) {
            return;
        }
        Op04StructuredStatement lutStaticInitCode = lutStaticInit.getAnalysis();

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(lutStaticInitCode);
        if (structuredStatements == null) return;

        // Filter out the comments.
        structuredStatements = Functional.filter(structuredStatements, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return !(in instanceof StructuredComment);
            }
        });


        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        WildcardMatch wcm1 = new WildcardMatch();
        WildcardMatch wcm2 = new WildcardMatch();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1,
                new MatchSequence(
                        new StructuredAssignment(lookupTable, new NewPrimitiveArray(
                                new ArrayLength(wcm1.getStaticFunction("func", enumObject.getInferredJavaType().getJavaTypeInstance(), null, "values")),
                                RawJavaType.INT)),
                        new KleeneStar(new ResetAfterTest(wcm2,
                                new MatchSequence(
                                        new StructuredTry(null, null, null),
                                        new BeginBlock(null),
                                        new StructuredAssignment(
                                                new ArrayVariable(
                                                        new ArrayIndex(
                                                                new LValueExpression(lookupTable),
                                                                wcm2.getMemberFunction("ordinal", "ordinal",
                                                                        new LValueExpression(
                                                                                wcm2.getStaticVariable("enumval", enumObject.getInferredJavaType().getJavaTypeInstance(), enumObject.getInferredJavaType())
                                                                        )
                                                                )
                                                        )
                                                ),
                                                wcm2.getExpressionWildCard("literal")
                                        ),
                                        new EndBlock(null),
                                        new StructuredCatch(null, null, null, null),
                                        new BeginBlock(null),
                                        new EndBlock(null)
                                )
                        ))
                )
        );

        SwitchForeignEnumMatchResultCollector matchResultCollector = new SwitchForeignEnumMatchResultCollector(wcm2);
        boolean matched = false;
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                // This really should only match once.  If it matches multiple times, something else
                // is being identically initialised, which is probably wrong!
                matched = true;
                break;
            }
        }

        if (!matched) {
            // If the switch is a completely empty enum switch, we can still remove the blank lookup initialiser.
            return;
        }

        Map<Integer, StaticVariable> reverseLut = matchResultCollector.getLUT();
        /*
         * Now, we rewrite the statement in the FIRST match (i.e in our original source file)
         * to use the reverse map of integer to enum value.
         *
         * We can only do the rewrite if ALL the entries in the case list are in the map we found above.
         */
        StructuredStatement structuredStatement;
        StructuredSwitch newSwitch;
        if (!expression) {
            StructuredSwitch structuredSwitch = mrc.getStructuredSwitch();
            structuredStatement = structuredSwitch;
            Op04StructuredStatement switchBlock = structuredSwitch.getBody();
            StructuredStatement switchBlockStatement = switchBlock.getStatement();
            if (!(switchBlockStatement instanceof Block)) {
                throw new IllegalStateException("Inside switch should be a block");
            }

            Block block = (Block) switchBlockStatement;
            List<Op04StructuredStatement> caseStatements = block.getBlockStatements();

        /*
         * If we can match every one of the ordinals, we replace the statement.
         */
            LinkedList<Op04StructuredStatement> newBlockContent = ListFactory.newLinkedList();
            InferredJavaType inferredJavaType = enumObject.getInferredJavaType();
            for (Op04StructuredStatement caseOuter : caseStatements) {
                StructuredStatement caseInner = caseOuter.getStatement();
                if (!(caseInner instanceof StructuredCase)) {
                    return;
                }
                StructuredCase caseStmt = (StructuredCase) caseInner;
                List<Expression> values = caseStmt.getValues();
                List<Expression> newValues = ListFactory.newList();
                for (Expression value : values) {
                    Integer iVal = getIntegerFromLiteralExpression(value);
                    if (iVal == null) {
                        return;
                    }
                    StaticVariable enumVal = reverseLut.get(iVal);
                    if (enumVal == null) {
                        return;
                    }
                    newValues.add(new LValueExpression(enumVal));
                }
                StructuredCase replacement = new StructuredCase(newValues, inferredJavaType, caseStmt.getBody(), caseStmt.getBlockIdentifier(), true);
                newBlockContent.add(new Op04StructuredStatement(replacement));
            }
            Block replacementBlock = new Block(newBlockContent, block.isIndenting());
            newSwitch = new StructuredSwitch(
                    enumObject,
                    new Op04StructuredStatement(replacementBlock),
                    structuredSwitch.getBlockIdentifier());
        } else {
            structuredStatement = mrc.getStructuredExpressionStatement();
            LinkedList<Op04StructuredStatement> tmp = new LinkedList<Op04StructuredStatement>();
            tmp.add(new Op04StructuredStatement(new StructuredComment("Empty switch")));
            newSwitch = new StructuredSwitch(
                    enumObject,
                    new Op04StructuredStatement(new Block(tmp, true)),
                    blockIdentifierFactory.getNextBlockIdentifier(BlockType.SWITCH));
        }


        structuredStatement.getContainer().replaceContainedStatement(newSwitch);
        enumLutClass.markHiddenInnerClass();
    }

    private Integer getIntegerFromLiteralExpression(Expression exp) {
        if (!(exp instanceof Literal)) {
            return null;
        }
        Literal literal = (Literal) exp;
        TypedLiteral typedLiteral = literal.getValue();
        if (typedLiteral.getType() != TypedLiteral.LiteralType.Integer) {
            return null;
        }
        return (Integer) typedLiteral.getValue();
    }

    private static class SwitchEnumMatchResultCollector extends AbstractMatchResultIterator {

        private LValue lookupTable;
        private Expression enumObject;
        private StructuredSwitch structuredSwitch;
        private StructuredExpressionStatement structuredExpressionStatement;

        private SwitchEnumMatchResultCollector() {
        }

        @Override
        public void clear() {
            lookupTable = null;
            enumObject = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            if (name.equals("switch")) {
                structuredSwitch = (StructuredSwitch) statement;
            } else if (name.equals("bodylessswitch")) {
                structuredExpressionStatement = (StructuredExpressionStatement)statement;
            }
        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            lookupTable = wcm.getLValueWildCard("lut").getMatch();
            enumObject = wcm.getExpressionWildCard("object").getMatch();
        }

        LValue getLookupTable() {
            return lookupTable;
        }

        Expression getEnumObject() {
            return enumObject;
        }

        StructuredSwitch getStructuredSwitch() {
            return structuredSwitch;
        }

        StructuredExpressionStatement getStructuredExpressionStatement() {
            return structuredExpressionStatement;
        }
    }

    private class SwitchForeignEnumMatchResultCollector extends AbstractMatchResultIterator {
        private final WildcardMatch wcmCase;
        private final Map<Integer, StaticVariable> lutValues = MapFactory.newMap();

        private SwitchForeignEnumMatchResultCollector(WildcardMatch wcmCase) {
            this.wcmCase = wcmCase;
        }

        Map<Integer, StaticVariable> getLUT() {
            return lutValues;
        }

        @Override
        public void clear() {

        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {

        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            if (wcm == wcmCase) {
                StaticVariable staticVariable = wcm.getStaticVariable("enumval").getMatch();

                Expression exp = wcm.getExpressionWildCard("literal").getMatch();
                Integer literalInt = getIntegerFromLiteralExpression(exp);
                if (literalInt == null) {
                    return;
                }
                lutValues.put(literalInt, staticVariable);
            }
        }
    }
}
