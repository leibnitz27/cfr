package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 03/04/2012
 * <p/>
 * This is all a bit ugly, with the random casting going on. But I think probably it would be worse to use
 * a multiple direction visitor....
 */
public class CreationCollector {
    private final Map<StackSSALabel, Pair<NewObject, StatementContainer>> creations = MapFactory.newMap();
    private final Map<StackSSALabel, Pair<MemberFunctionInvokation, StatementContainer>> constructions = MapFactory.newMap();

    private <X> void mark(Map<StackSSALabel, Pair<X, StatementContainer>> map, StackSSALabel lValue, X rValue, StatementContainer container) {
        if (map.containsKey(lValue)) {
            map.put(lValue, null);
        } else {
            map.put(lValue, new Pair<X, StatementContainer>(rValue, container));
        }
    }

    public void collectCreation(LValue lValue, Expression rValue, StatementContainer container) {
        if (!(rValue instanceof NewObject)) return;
        if (!(lValue instanceof StackSSALabel)) return;
        mark(creations, (StackSSALabel) lValue, (NewObject) rValue, container);
    }

    public void collectConstruction(Expression expression, MemberFunctionInvokation rValue, StatementContainer container) {
        if (!(expression instanceof StackValue)) return;
        StackSSALabel lValue = ((StackValue) expression).getStackValue();
        mark(constructions, lValue, rValue, container);
    }

    /*
    *
    */
    public void condenseConstructions() {
        for (Map.Entry<StackSSALabel, Pair<MemberFunctionInvokation, StatementContainer>> construction : constructions.entrySet()) {
            StackSSALabel lValue = construction.getKey();
            Pair<MemberFunctionInvokation, StatementContainer> constructionValue = construction.getValue();
            if (constructionValue == null) continue;
            Pair<NewObject, StatementContainer> creationValue = creations.get(lValue);
            if (creationValue == null) continue;

            MemberFunctionInvokation memberFunctionInvokation = constructionValue.getFirst();
            NewObject newObject = creationValue.getFirst();

            AbstractConstructorInvokation constructorInvokation = null;
            if (newObject.getType().getTypeInstance().getInnerClassHereInfo().isAnoynmousInnerClass()) {
                constructorInvokation = new ConstructorInvokationAnoynmousInner(
                        memberFunctionInvokation.getCp(),
                        newObject.getType(),
                        memberFunctionInvokation.getArgs());
            } else {
                constructorInvokation = new ConstructorInvokationSimple(
                        memberFunctionInvokation.getFunction(),
                        newObject.getType(),
                        memberFunctionInvokation.getArgs());
            }

            AssignmentSimple replacement = new AssignmentSimple(lValue, constructorInvokation);

            lValue.getStackEntry().decrementUsage();
            StatementContainer constructionContainer = constructionValue.getSecond();
            StatementContainer creationContainer = creationValue.getSecond();
            creationContainer.nopOut();
            constructionContainer.replaceStatement(replacement);
        }
    }
}
