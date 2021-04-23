package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.AnonymousClassUsage;
import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;

/**
 * This is all a bit ugly, with the random casting going on. But I think probably it would be worse to use
 * a multiple indirection visitor....
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

    private final List<Pair<LValue, StatementPair<MemberFunctionInvokation>>> collectedConstructions = ListFactory.newList();
    private final Map<LValue, List<StatementContainer>> collectedCreations = MapFactory.newLazyMap(new UnaryFunction<LValue, List<StatementContainer>>() {
        @Override
        public List<StatementContainer> invoke(LValue arg) {
            return ListFactory.newList();
        }
    });

    private final AnonymousClassUsage anonymousClassUsage;

    public CreationCollector(AnonymousClassUsage anonymousClassUsage) {
        this.anonymousClassUsage = anonymousClassUsage;
    }

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
        if (expression instanceof NewObject) {
            markConstruction(null, rValue, container);
            return;
        }
    }


    private void markConstruction(LValue lValue, MemberFunctionInvokation rValue, StatementContainer container) {
        collectedConstructions.add(Pair.make(lValue, new StatementPair<MemberFunctionInvokation>(rValue, container)));
    }

    /*
    *
    */
    public void condenseConstructions(Method method, DCCommonState dcCommonState) {

        Map<LValue, StatementContainer> constructionTargets = MapFactory.newMap();

        for (Pair<LValue, StatementPair<MemberFunctionInvokation>> construction : collectedConstructions) {
            LValue lValue = construction.getFirst();
            StatementPair<MemberFunctionInvokation> constructionValue = construction.getSecond();
            if (constructionValue == null) continue;

            InstrIndex idx = constructionValue.getLocation().getIndex();
            if (lValue != null) {
                if (!collectedCreations.containsKey(lValue)) continue;
                List<StatementContainer> creations = collectedCreations.get(lValue);
                boolean found = false;
                for (StatementContainer creation : creations) {
                    // This is a terrible heuristic.
                    if (creation.getIndex().isBackJumpFrom(idx)) {
                        found = true;
                        break;
                    }
                }
                if (!found) continue;
            }

            MemberFunctionInvokation memberFunctionInvokation = constructionValue.getValue();
            JavaTypeInstance lValueType = memberFunctionInvokation.getClassTypeInstance();
            InferredJavaType inferredJavaType = lValue == null ? memberFunctionInvokation.getInferredJavaType() : lValue.getInferredJavaType();


            AbstractConstructorInvokation constructorInvokation = null;
            InnerClassInfo innerClassInfo = lValueType.getInnerClassHereInfo();

            if (innerClassInfo.isAnonymousClass()) {
                /* anonymous inner class - so we need to match the arguments we're deliberately passing
                 * (i.e. the ones which are being passed into the constructor for the base of the anonymous
                 * class), vs ones which are being bound without being passed in.
                 */
                ConstructorInvokationAnonymousInner constructorInvokationAnonymousInner = new ConstructorInvokationAnonymousInner(
                        BytecodeLoc.NONE, // Embedded in the invokation.
                        memberFunctionInvokation,
                        inferredJavaType,
                        memberFunctionInvokation.getArgs(),
                        dcCommonState, lValueType);
                /*
                 * Since java9, see https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8034044
                 * inner classes have lost their lovely static attribute.  This is a giant pain.
                 */
                constructorInvokation = constructorInvokationAnonymousInner;
                ClassFile classFile = constructorInvokationAnonymousInner.getClassFile();
                if (classFile != null) {
                    anonymousClassUsage.note(classFile, constructorInvokationAnonymousInner);
                    /*
                     * And we take a best guess at the type of the anonymous class - it's either the interface
                     * or the concrete class the anonymous type is derived from.
                     */
                    JavaTypeInstance anonymousTypeBase = ClassFile.getAnonymousTypeBase(classFile);
                    inferredJavaType.forceDelegate(new InferredJavaType(anonymousTypeBase, InferredJavaType.Source.UNKNOWN));
                    /*
                     * However, if we're in java 10 or higher, this anonymous class could be being referred to as var.
                     * this matters because we can do
                     *
                     * var x = new Object(){int bob = 3};
                     * x.bob;
                     */
                    if (classFile.getClassFileVersion().equalOrLater(ClassFileVersion.JAVA_10)) {
                        inferredJavaType.shallowSetCanBeVar();
                    }
                } else {

                    BindingSuperContainer bindingSuperContainer = lValueType.getBindingSupers();
                    // This may be null, if we simply don't have the information present.
                    if (bindingSuperContainer != null) {
                        JavaTypeInstance bestGuess = bindingSuperContainer.getMostLikelyAnonymousType(lValueType);
                        // We don't want to FORCE the delegate, just allow it to be cast back to the most likely type...
                        inferredJavaType.forceDelegate(new InferredJavaType(bestGuess, InferredJavaType.Source.UNKNOWN));
                    }
                }
            }

            if (constructorInvokation == null) {
                InferredJavaType constructionType = new InferredJavaType(lValueType, InferredJavaType.Source.CONSTRUCTOR);
                // We'd prefer to use inferredJavaType here, as it can capture generic information
                // What would be *nicer* to do is to chain the generic information for the captured
                // construction type.
                if (inferredJavaType.getJavaTypeInstance() instanceof JavaGenericBaseInstance) {
                    constructionType = inferredJavaType;
                }

                ConstructorInvokationSimple cis = new ConstructorInvokationSimple(
                        BytecodeLoc.NONE, // in the invokation
                        memberFunctionInvokation,
                        inferredJavaType,
                        constructionType,
                        memberFunctionInvokation.getArgs());

                constructorInvokation = cis;

                if (innerClassInfo.isMethodScopedClass()) {
                    // TODO:  Feels like the concept of anonymousClassUsage and methodScopedClass
                    // can be run together in the method, NOT in the class.
                    method.markUsedLocalClassType(lValueType);
                    try {
                        ClassFile cls = dcCommonState.getClassFile(lValueType);
                        anonymousClassUsage.noteMethodClass(cls, cis);
                    } catch (CannotLoadClassException ignore) {
                    }
                }
            }

            Statement replacement = null;
            if (lValue == null) {
                replacement = new ExpressionStatement(constructorInvokation);
            } else {
                replacement = new AssignmentSimple(constructorInvokation.getLoc(), lValue, constructorInvokation);

                if (lValue instanceof StackSSALabel) {
                    StackSSALabel stackSSALabel = (StackSSALabel) lValue;
                    StackEntry stackEntry = stackSSALabel.getStackEntry();
                    stackEntry.decrementUsage();
                    stackEntry.incSourceCount();
                }
            }
            StatementContainer constructionContainer = constructionValue.getLocation();
            //noinspection unchecked
            constructionContainer.replaceStatement(replacement);
            if (lValue != null) {
                constructionTargets.put(lValue, constructionContainer);
            }
        }

        for (Map.Entry<LValue, List<StatementContainer>> creations : collectedCreations.entrySet()) {
            LValue lValue = creations.getKey();
            for (StatementContainer statementContainer : creations.getValue()) {
                if (lValue instanceof StackSSALabel) {
                    StackEntry stackEntry = ((StackSSALabel) lValue).getStackEntry();
                    stackEntry.decSourceCount();
                }
                // If the statement immediately after statement container dup'd this, move it after the construction.
                StatementContainer x = constructionTargets.get(lValue);
                if (x != null) {
                    // If we've orphaned a dup, copy if after the target.
                    moveDupPostCreation(lValue, statementContainer, x);
                }
                statementContainer.nopOut();
            }
        }
    }

    private void moveDupPostCreation(LValue lValue, StatementContainer oldCreation, StatementContainer oldConstruction) {
        Op03SimpleStatement creatr = (Op03SimpleStatement)oldCreation;
        Op03SimpleStatement constr = (Op03SimpleStatement)oldConstruction;
        Op03SimpleStatement cretgt = creatr.getTargets().get(0);
        if (constr == cretgt) return;
        if (cretgt.getSources().size() != 1) return;
        Statement s = cretgt.getStatement();
        if (s instanceof ExpressionStatement) {
            Expression e = ((ExpressionStatement) s).getExpression();
            if (!(e instanceof StackValue)) return;
        } else
            if (s instanceof AssignmentSimple) {
            Expression rv = s.getRValue();
            if (!(rv instanceof StackValue)) return;
            if (!((StackValue) rv).getStackValue().equals(lValue)) return;
            if (!(s.getCreatedLValue() instanceof StackSSALabel)) return;
        } else {
            return;
        }
        cretgt.splice(constr);
    }
}
