package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

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
    private final Map<LValue, List<StatementContainer>> collectedCreations = MapFactory.newLazyMap(new UnaryFunction<LValue, List<StatementContainer>>() {
        @Override
        public List<StatementContainer> invoke(LValue arg) {
            return ListFactory.newList();
        }
    });

    public void collectCreation(LValue lValue, Expression rValue, StatementContainer container) {
        if (!(rValue instanceof NewObject)) return;
        if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) return;
        collectedCreations.get(lValue).add(container);
    }

    public void collectConstruction(Expression expression, MemberFunctionInvokation rValue, StatementContainer container) {
        /*
         * We shouldn't collect a construction to turn it into a temp new IF the construction is, say, this.
         *
         * We cheat, and check that the LValue has been new'd SOMEWHERE before this.
         */

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
        collectedConstructions.add(new Triple(lValue, null, new StatementPair<MemberFunctionInvokation>(rValue, container)));
    }

    /*
    *
    */
    public void condenseConstructions(DCCommonState dcCommonState) {

        for (Triple construction : collectedConstructions) {
            LValue lValue = construction.getlValue();
            StatementPair<MemberFunctionInvokation> constructionValue = construction.getConstruction();
            if (constructionValue == null) continue;

            InstrIndex idx = constructionValue.getLocation().getIndex();
            if (!collectedCreations.containsKey(lValue)) continue;
            List<StatementContainer> creations = collectedCreations.get(lValue);
            boolean found = false;
            for (StatementContainer creation : creations) {
                if (creation.getIndex().isBackJumpFrom(idx)) {
                    found = true;
                    break;
                }
            }
            if (!found) continue;

//            StatementPair<NewObject> creationValue = construction.getCreation();
//            if (creationValue == null) continue;

            MemberFunctionInvokation memberFunctionInvokation = constructionValue.getValue();
//            NewObject newObject = creationValue.getValue();
            JavaTypeInstance lValueType = memberFunctionInvokation.getClassTypeInstance();
//            InferredJavaType inferredJavaType = new InferredJavaType(lValueType, InferredJavaType.Source.EXPRESSION, true);
            InferredJavaType inferredJavaType = lValue.getInferredJavaType();


            AbstractConstructorInvokation constructorInvokation = null;
            if (lValueType.getInnerClassHereInfo().isAnoynmousInnerClass()) {
                /* anonymous inner class - so we need to match the arguments we're deliberately passing
                 * (i.e. the ones which are being passed into the constructor for the base of the anonymous
                 * class), vs ones which are being bound without being passed in.
                 */
                constructorInvokation = new ConstructorInvokationAnoynmousInner(
                        memberFunctionInvokation,
                        inferredJavaType,
                        memberFunctionInvokation.getArgs(),
                        dcCommonState);
            } else {
                constructorInvokation = new ConstructorInvokationSimple(
                        memberFunctionInvokation,
                        inferredJavaType,
                        memberFunctionInvokation.getArgs());
            }

            AssignmentSimple replacement = new AssignmentSimple(lValue, constructorInvokation);

            if (lValue instanceof StackSSALabel) {
                StackSSALabel stackSSALabel = (StackSSALabel) lValue;
                StackEntry stackEntry = stackSSALabel.getStackEntry();
                stackEntry.decrementUsage();
                stackEntry.incSourceCount();
            }
            StatementContainer constructionContainer = constructionValue.getLocation();
//            StatementContainer creationContainer = creationValue.getLocation();
//            creationContainer.nopOut();
            constructionContainer.replaceStatement(replacement);
        }

        for (Map.Entry<LValue, List<StatementContainer>> creations : collectedCreations.entrySet()) {
            LValue lValue = creations.getKey();
            for (StatementContainer statementContainer : creations.getValue()) {
                if (lValue instanceof StackSSALabel) {
                    StackEntry stackEntry = ((StackSSALabel) lValue).getStackEntry();
                    stackEntry.decSourceCount();
                }
                statementContainer.nopOut();
            }
        }

    }
}
