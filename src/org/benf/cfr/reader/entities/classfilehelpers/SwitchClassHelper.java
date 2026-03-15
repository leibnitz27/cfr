package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewAnonymousArray;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;
import java.util.Map;

public class SwitchClassHelper {
    public static Map<Integer, StaticVariable> getSwitchEntriesByOrdinal(DCCommonState dcCommonState, Expression lvalue) {
        ClassFile enumClass;
        try {
            enumClass = dcCommonState.getClassFile(lvalue.getInferredJavaType().getJavaTypeInstance());
        } catch (CannotLoadClassException e) {
            // Oh dear, can't load that class.  Proceed without it.
            return null;
        }
        Method values = enumClass.getSingleMethodByNameOrNull("$values");
        if (values == null) {
            return null;
        }
        Op04StructuredStatement valuesData = null;
        try {
            valuesData = values.getAnalysis();
        } catch (ConfusedCFRException e) {
            return null;
        }

        List<StructuredStatement> decls = ListFactory.newList();
        valuesData.linearizeStatementsInto(decls);
        decls = Functional.filter(decls, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return !(in instanceof StructuredComment || in instanceof BeginBlock || in instanceof EndBlock) ;
            }
        });

        if (decls.size() != 1) return null;

        StructuredStatement decl = decls.get(0);
        if (!(decl instanceof StructuredReturn)) return null;

        Expression value = ((StructuredReturn) decl).getValue();
        if (!(value instanceof NewAnonymousArray)) return null;

        List<Expression> enumValues = ((NewAnonymousArray) value).getValues();

        int idx = 0;
        Map<Integer, StaticVariable> ordinals = MapFactory.newMap();
        for (Expression e : enumValues) {
            if (!(e instanceof LValueExpression)) return null;
            LValue lv = ((LValueExpression) e).getLValue();
            if (!(lv instanceof StaticVariable)) return null;
            if (!lv.getInferredJavaType().getJavaTypeInstance().equals(lvalue.getInferredJavaType().getJavaTypeInstance()))
                return null;
            ordinals.put(idx++, (StaticVariable) lv);
        }
        return ordinals;
    }
}
