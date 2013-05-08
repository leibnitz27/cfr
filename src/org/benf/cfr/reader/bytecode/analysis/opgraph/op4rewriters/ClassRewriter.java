package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.*;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractNewArray;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewAnonymousArray;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.getopt.CFRState;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 07/05/2013
 * Time: 05:47
 */
public class ClassRewriter {

    public static void rewriteEnumClass(ClassFile classFile, CFRState state) {

        JavaTypeInstance classType = classFile.getClassType();
        JavaTypeInstance baseType = classFile.getBaseClassType();

        JavaTypeInstance enumType = TypeConstants.ENUM;

        if (!(baseType instanceof JavaGenericRefTypeInstance)) return;
        JavaGenericRefTypeInstance genericBaseType = (JavaGenericRefTypeInstance) baseType;
        if (!genericBaseType.getDeGenerifiedType().equals(enumType)) return;
        // It's an enum type, is it Enum<classType> ?
        List<JavaTypeInstance> boundTypes = genericBaseType.getGenericTypes();
        if (boundTypes == null || boundTypes.size() != 1) return;
        if (!boundTypes.get(0).equals(classType)) return;

        ClassRewriter c = new ClassRewriter(classFile, classType, state);
        c.rewrite();
    }

    private final ClassFile classFile;
    private final JavaTypeInstance classType;
    private final CFRState state;
    private final InferredJavaType clazzIJT;


    public ClassRewriter(ClassFile classFile, JavaTypeInstance classType, CFRState state) {
        this.classFile = classFile;
        this.classType = classType;
        this.state = state;
        this.clazzIJT = new InferredJavaType(classType, InferredJavaType.Source.UNKNOWN, true);
    }

    private boolean rewrite() {
        // Ok, it's an enum....
        // Verify the static initialiser - for each instance of enm created in there, make sure
        // it's a public static enum member.
        // Verify that the values field is also valid.
        // Nop out this code from the static initialiser.  There MAY be code left, if the enum
        // actually HAD a static initialiser!

        Method staticInit = null;
        try {
            staticInit = classFile.getMethodByName(MiscConstants.STATIC_INIT_METHOD);
        } catch (NoSuchMethodException e) {
            // Should have a static constructor.
            throw new ConfusedCFRException("No static init method on enum");
        }

        Op04StructuredStatement staticInitCode = staticInit.getAnalysis();
        if (!staticInitCode.isFullyStructured()) return false;
        EnumInitMatchCollector initMatchCollector = analyseStaticMethod(staticInitCode);
        if (initMatchCollector == null) return false;

        return true;
    }

    /*
     * Expect the static initialiser to be in a fairly common format - i.e. we will
     * NOT attempt to deal with obfuscation.
     */
    private EnumInitMatchCollector analyseStaticMethod(Op04StructuredStatement statement) {
        List<StructuredStatement> statements = ListFactory.newList();
        statement.linearizeStatementsInto(statements);

        // Filter out the comments.
        statements = Functional.filter(statements, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return !(in instanceof StructuredComment);
            }
        });

        WildcardMatch wcm = new WildcardMatch();

        InferredJavaType clazzIJT = new InferredJavaType(classType, InferredJavaType.Source.UNKNOWN, true);
        JavaTypeInstance arrayType = new JavaArrayTypeInstance(1, classType);
        InferredJavaType clazzAIJT = new InferredJavaType(arrayType, InferredJavaType.Source.UNKNOWN, true);
        Matcher<StructuredStatement> matcher = new MatchSequence(
                new BeginBlock(),
                new KleenePlus(new ResetAfterTest(wcm, new CollectMatch("entry", new StructuredAssignment(wcm.getStaticVariable("e", classType, clazzIJT), wcm.getConstructorSimpleWildcard("c", classType))))),
                new ResetAfterTest(wcm, new CollectMatch("values", new StructuredAssignment(wcm.getStaticVariable("v", classType, clazzAIJT), wcm.getNewArrayWildCard("v", 0, 1))))
        );

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);
        EnumInitMatchCollector matchCollector = new EnumInitMatchCollector(wcm);

        mi.advance();
        if (!matcher.match(mi, matchCollector)) {
            return null;
        }

        if (!matchCollector.isValid()) return null;
        return matchCollector;
    }

    private static class CollectedEnumData<T> {
        private final Op04StructuredStatement container;
        private final T data;

        private CollectedEnumData(Op04StructuredStatement container, T data) {
            this.container = container;
            this.data = data;
        }

        private Op04StructuredStatement getContainer() {
            return container;
        }

        private T getData() {
            return data;
        }
    }

    private class EnumInitMatchCollector implements MatchResultCollector {

        private final WildcardMatch wcm;
        private final Map<StaticVariable, CollectedEnumData<ConstructorInvokationSimple>> entryMap = MapFactory.newMap();
        private CollectedEnumData<NewAnonymousArray> matchedArray;
        private List<Field> matchedHideTheseFields = ListFactory.newList();

        private EnumInitMatchCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {

        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            if (name.equals("entry")) {
                StaticVariable staticVariable = wcm.getStaticVariable("e").getMatch();
                ConstructorInvokationSimple constructorInvokation = wcm.getConstructorSimpleWildcard("c").getMatch();
                entryMap.put(staticVariable, new CollectedEnumData<ConstructorInvokationSimple>(statement.getContainer(), constructorInvokation));
                return;
            }

            if (name.equals("values")) {
                AbstractNewArray abstractNewArray = wcm.getNewArrayWildCard("v").getMatch();
                // We need this to have been successfully desugared...
                if (abstractNewArray instanceof NewAnonymousArray) {
                    matchedArray = new CollectedEnumData<NewAnonymousArray>(statement.getContainer(), (NewAnonymousArray) abstractNewArray);
                }
                return;
            }
        }

        @Override
        public void collectMatches(WildcardMatch wcm) {
        }

        boolean isValid() {
            /*
             * Validate that the entries of the "values" array are those of the enum entries.
             *
             * Examine all static members, make sure they're in this set.
             */
            List<Field> fields = classFile.getFields();
            ConstantPool cp = classFile.getConstantPool();
            for (Field field : fields) {
                JavaTypeInstance fieldType = field.getJavaTypeInstance(cp);
                boolean isStatic = field.testAccessFlag(AccessFlag.ACC_STATIC);
                boolean isEnum = field.testAccessFlag(AccessFlag.ACC_ENUM);
                boolean expected = (isStatic && isEnum && fieldType.equals(classType));
                StaticVariable tmp = new StaticVariable(clazzIJT, classType, field.getFieldName(cp));
                if (expected != entryMap.containsKey(tmp)) {
                    return false;
                }
                if (expected) {
                    matchedHideTheseFields.add(field);
                }
            }
            List<Expression> values = matchedArray.getData().getValues();
            if (values.size() != entryMap.size()) {
                return false;
            }
            for (Expression value : values) {
                if (!(value instanceof LValueExpression)) {
                    return false;
                }
                LValueExpression lValueExpression = (LValueExpression) value;
                LValue lvalue = lValueExpression.getLValue();
                if (!(lvalue instanceof StaticVariable)) {
                    return false;
                }
                StaticVariable staticVariable = (StaticVariable) lvalue;
                if (!entryMap.containsKey(staticVariable)) {
                    return false;
                }
            }
            LValue valuesArray = ((StructuredAssignment) (matchedArray.getContainer().getStatement())).getLvalue();
            if (!(valuesArray instanceof StaticVariable)) {
                return false;
            }
            StaticVariable valuesArrayStatic = (StaticVariable) valuesArray;
            try {
                Field valuesField = classFile.getFieldByName(valuesArrayStatic.getVarName());
                if (!valuesField.testAccessFlag(AccessFlag.ACC_STATIC)) {
                    return false;
                }
                matchedHideTheseFields.add(valuesField);
            } catch (NoSuchFieldException e) {
                return false;
            }
            return true;
        }
    }
}
