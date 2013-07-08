package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;

import java.util.List;
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

    private static class StatementPair<X> {
        private final X value;
        private final StatementContainer location;

        private StatementPair(X value, StatementContainer location) {
            this.value = value;
            this.location = location;
        }

        private X getValue() {
            return value;
        }

        private StatementContainer getLocation() {
            return location;
        }
    }

    private static class Triple {
        private final LValue lValue;
        private final StatementPair<NewObject> creation;
        private final StatementPair<MemberFunctionInvokation> construction;

        private Triple(LValue lValue, StatementPair<NewObject> creation, StatementPair<MemberFunctionInvokation> construction) {
            this.lValue = lValue;
            this.creation = creation;
            this.construction = construction;
        }

        private LValue getlValue() {
            return lValue;
        }

        private StatementPair<NewObject> getCreation() {
            return creation;
        }

        private StatementPair<MemberFunctionInvokation> getConstruction() {
            return construction;
        }
    }

    private final List<Triple> collectedConstructions = ListFactory.newList();
    private final Map<LValue, StatementPair<NewObject>> pendingCreations = MapFactory.newMap();

    private <X> void mark(Map<LValue, StatementPair<X>> map, LValue lValue, X rValue, StatementContainer container) {
        if (map.containsKey(lValue)) {
            map.put(lValue, null);
        } else {
            map.put(lValue, new StatementPair<X>(rValue, container));
        }
    }

    public void markJump() {
        pendingCreations.clear();
    }

    public void collectCreation(LValue lValue, Expression rValue, StatementContainer container) {
        if (!(rValue instanceof NewObject)) return;
        if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) return;
        mark(pendingCreations, lValue, (NewObject) rValue, container);
    }

    public void collectConstruction(Expression expression, MemberFunctionInvokation rValue, StatementContainer container) {
        if (expression instanceof StackValue) {
            StackSSALabel lValue = ((StackValue) expression).getStackValue();
            markConstruction(lValue, rValue, container);
            return;
        }
        if (expression instanceof LValueExpression) {
            LValue lValue = ((LValueExpression) expression).getLValue();
            markConstruction(lValue, rValue, container);
            return;
        }
    }


    private void markConstruction(LValue lValue, MemberFunctionInvokation rValue, StatementContainer container) {
        StatementPair<NewObject> newObj = pendingCreations.get(lValue);
        if (newObj == null) return;
        pendingCreations.remove(lValue);
        collectedConstructions.add(new Triple(lValue, newObj, new StatementPair<MemberFunctionInvokation>(rValue, container)));
    }

    /*
    *
    */
    public void condenseConstructions() {
        for (Triple construction : collectedConstructions) {
            LValue lValue = construction.getlValue();
            StatementPair<MemberFunctionInvokation> constructionValue = construction.getConstruction();
            if (constructionValue == null) continue;
            StatementPair<NewObject> creationValue = construction.getCreation();
            if (creationValue == null) continue;

            MemberFunctionInvokation memberFunctionInvokation = constructionValue.getValue();
            NewObject newObject = creationValue.getValue();

            AbstractConstructorInvokation constructorInvokation = null;
            if (newObject.getType().getTypeInstance().getInnerClassHereInfo().isAnoynmousInnerClass()) {
                /* anonymous inner class - so we need to match the arguments we're deliberately passing
                 * (i.e. the ones which are being passed into the constructor for the base of the anonymous
                 * class), vs ones which are being bound without being passed in.
                 */
                constructorInvokation = new ConstructorInvokationAnoynmousInner(
                        memberFunctionInvokation,
                        newObject.getInferredJavaType(),
                        memberFunctionInvokation.getArgs());
            } else {
                constructorInvokation = new ConstructorInvokationSimple(
                        newObject.getInferredJavaType(),
                        memberFunctionInvokation.getArgs());
            }

            AssignmentSimple replacement = new AssignmentSimple(lValue, constructorInvokation);

            if (lValue instanceof StackSSALabel) {
                ((StackSSALabel) lValue).getStackEntry().decrementUsage();
            }
            StatementContainer constructionContainer = constructionValue.getLocation();
            StatementContainer creationContainer = creationValue.getLocation();
            creationContainer.nopOut();
            constructionContainer.replaceStatement(replacement);
        }
    }
}
