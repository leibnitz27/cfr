package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.ConstructorUtils;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredDefinition;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Similar to the static lifter, however this has to cope with the possibility that EVERY constructor
 * will have had the non static initialisation pushed into it.
 */
public class NonStaticLifter {

    private final ClassFile classFile;

    public NonStaticLifter(ClassFile classFile) {
        this.classFile = classFile;
    }

    public void liftNonStatics() {

        // All uninitialised non-static fields, in definition order.
        Pair<List<ClassFileField>, List<ClassFileField>> fields =  Functional.partition(classFile.getFields(), new Predicate<ClassFileField>() {
            @Override
            public boolean test(ClassFileField in) {
                if (in.getField().testAccessFlag(AccessFlag.ACC_STATIC)) return false;
                if (in.getField().testAccessFlag(AccessFlag.ACC_SYNTHETIC)) return false;
                // Members may well have an initial value. If they do, we need to make sure that it is
                // exactly the same as the one we're lifting, or we abort.
                return true;
            }
        });
        LinkedList<ClassFileField> classFileFields = new LinkedList<ClassFileField>(fields.getFirst());
        Map<String, ClassFileField> other = MapFactory.newMap();
        for (ClassFileField otherField : fields.getSecond()) {
            other.put(otherField.getFieldName(), otherField);
        }
        if (classFileFields.isEmpty()) return;
        Map<String, Pair<Integer, ClassFileField>> fieldMap = MapFactory.newMap();
        for (int x = 0, len = classFileFields.size(); x < len; ++x) {
            ClassFileField classFileField = classFileFields.get(x);
            fieldMap.put(classFileField.getField().getFieldName(), Pair.make(x, classFileField));
        }

        List<Method> constructors = Functional.filter(classFile.getConstructors(), new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                return !ConstructorUtils.isDelegating(in);
            }
        });

        /* These constructors are ones which do not delegate, i.e. we would expect them to share common initialisation
         * code.  (If they don't it's not the end of the world, we're tidying up).
         */

        List<List<Op04StructuredStatement>> constructorCodeList = ListFactory.newList();
        int minSize = Integer.MAX_VALUE;
        for (Method constructor : constructors) {
            List<Op04StructuredStatement> blockStatements = MiscStatementTools.getBlockStatements(constructor.getAnalysis());
            if (blockStatements == null) return;
            blockStatements = Functional.filter(blockStatements, new Predicate<Op04StructuredStatement>() {
                @Override
                public boolean test(Op04StructuredStatement in) {
                    StructuredStatement stm = in.getStatement();
                    // We can skip comments and definitions - they won't have any effect on meaning of assignment to
                    // members.
                    if (stm instanceof StructuredComment) return false;
                    if (stm instanceof StructuredDefinition) return false;
                    return true;
                }
            });
            if (blockStatements.isEmpty()) return;

            /*
             * If the first statement is a super init, we trim that.
             * Bit inefficient.
             */
            StructuredStatement superTest = blockStatements.get(0).getStatement();
            if (superTest instanceof StructuredExpressionStatement) {
                Expression expression = ((StructuredExpressionStatement) superTest).getExpression();
                if (expression instanceof SuperFunctionInvokation) blockStatements.remove(0);
            }

            constructorCodeList.add(blockStatements);
            if (blockStatements.size() < minSize) minSize = blockStatements.size();
        }

        if (constructorCodeList.isEmpty()) return;

        /*
         * We have to be more involved than in a static constructor - each of the statements has to match.
         */
        int numConstructors = constructorCodeList.size();
        final List<Op04StructuredStatement> constructorCode = constructorCodeList.get(0);
        if (constructorCode.isEmpty()) return; // can't happen.
        Set<Expression> usedFvs = SetFactory.newSet();
        for (int x = 0; x < minSize; ++x) {
            StructuredStatement s1 = constructorCode.get(x).getStatement();
            for (int y = 1; y < numConstructors; ++y) {
                StructuredStatement sOther = constructorCodeList.get(y).get(x).getStatement();
                if (!s1.equals(sOther)) return;
            }

            /*
             * Ok, they're all the same.  Now, is this an assignment to a member, AND does it use only other fields,
             * which have already been initialised? (and are not forward references) Sheeeesh....
             */
            if (!(s1 instanceof StructuredAssignment)) return;
            StructuredAssignment structuredAssignment = (StructuredAssignment) s1;
            LValue lValue = structuredAssignment.getLvalue();
            if (!(lValue instanceof FieldVariable)) return;
            FieldVariable fieldVariable = (FieldVariable) lValue;
            if (!fromThisClass(fieldVariable)) return;

            /*
             * Ok, every field before this (which has been initialised) is usable.  But nothing else....
             * Unless it's synthetic!
             */
            Expression rValue = structuredAssignment.getRvalue();
            if (!tryLift(fieldVariable, rValue, fieldMap, usedFvs)) {
                ClassFileField f = other.get(fieldVariable.getFieldName());
                if (f == null) return;
                Field field = f.getField();
                if (field.testAccessFlag(AccessFlag.ACC_SYNTHETIC) && !field.testAccessFlag(AccessFlag.ACC_STATIC)) {
                    // we can't mark it as liftable, but we shouldn't block on it.
                    continue;
                }
                return;
            }
            usedFvs.add(fieldVariable.getObject());
            for (List<Op04StructuredStatement> constructorCodeLst1 : constructorCodeList) {
                constructorCodeLst1.get(x).nopOut();
            }
        }


    }

    private boolean fromThisClass(FieldVariable fv) {
        return fv.getOwningClassType().equals(classFile.getClassType());
    }

    private boolean tryLift(FieldVariable lValue, Expression rValue, Map<String, Pair<Integer, ClassFileField>> fieldMap,
                            Set<Expression> usedFvs) {
        Pair<Integer, ClassFileField> thisField = fieldMap.get(lValue.getFieldName());
        if (thisField == null) return false;
        ClassFileField classFileField = thisField.getSecond();
        if (!hasLegitArgs(rValue, usedFvs)) return false;
        // Ok, it doesn't use anything it shouldn't - change the initialiser.
        classFileField.setInitialValue(rValue);
        return true;
    }

    private boolean hasLegitArgs(Expression rValue, Set<Expression> usedFvs) {
        LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
        rValue.collectUsedLValues(usageCollector);
        for (LValue usedLValue : usageCollector.getUsedLValues()) {
            if (usedLValue instanceof StaticVariable) {
                // that's ok, these must have been initialised first.
                continue;
            }
            if (usedLValue instanceof FieldVariable) {
                if (!usedFvs.contains(((FieldVariable) usedLValue).getObject())) return false;
                continue;
            }
            if (usedLValue instanceof LocalVariable) {
                // The only localVariable we can get away with here is 'this'.

                LocalVariable variable = (LocalVariable)usedLValue;
                if (variable.getInferredJavaType().getJavaTypeInstance() == this.classFile.getClassType() &&
                    variable.getName().getStringName().equals(MiscConstants.THIS)) {
                    continue;
                }
            }
            // Other lvalue - can't allow.
            return false;
        }
        return true;
    }


}
