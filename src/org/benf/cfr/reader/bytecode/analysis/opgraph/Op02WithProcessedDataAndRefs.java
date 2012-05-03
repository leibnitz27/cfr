package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.opcode.DecodedLookupSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedTableSwitch;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntryHolder;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.exceptions.ExceptionBookmark;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
public class Op02WithProcessedDataAndRefs implements Dumpable, Graph<Op02WithProcessedDataAndRefs> {
    private InstrIndex index;

    private final JVMInstr instr;
    private final int originalRawOffset;
    private final byte[] rawData;
    private final ExceptionBookmark exceptionBookmark;
    private final List<Op02WithProcessedDataAndRefs> targets = ListFactory.newList();
    private final List<Op02WithProcessedDataAndRefs> sources = ListFactory.newList();
    private final ConstantPool cp;
    private final ConstantPoolEntry[] cpEntries;
    private long stackDepthBeforeExecution = -1;
    private long stackDepthAfterExecution;
    private final List<StackEntryHolder> stackConsumed = ListFactory.newList();
    private final List<StackEntryHolder> stackProduced = ListFactory.newList();

    public Op02WithProcessedDataAndRefs(JVMInstr instr, byte[] rawData, int index, ConstantPool cp, ConstantPoolEntry[] cpEntries, int originalRawOffset) {
        this(instr, rawData, new InstrIndex(index), cp, cpEntries, originalRawOffset, null);
    }

    public Op02WithProcessedDataAndRefs(JVMInstr instr, byte[] rawData, InstrIndex index, ConstantPool cp, ConstantPoolEntry[] cpEntries, int originalRawOffset, ExceptionBookmark exceptionBookmark) {
        this.instr = instr;
        this.rawData = rawData;
        this.index = index;
        this.cp = cp;
        this.cpEntries = cpEntries;
        this.originalRawOffset = originalRawOffset;
        this.exceptionBookmark = exceptionBookmark;
    }

    public InstrIndex getIndex() {
        return index;
    }

    public void addTarget(Op02WithProcessedDataAndRefs node) {
        targets.add(node);
    }

    public void addSource(Op02WithProcessedDataAndRefs node) {
        sources.add(node);
    }

    public void replaceTarget(Op02WithProcessedDataAndRefs oldTarget, Op02WithProcessedDataAndRefs newTarget) {
        int index = targets.indexOf(oldTarget);
        if (index == -1) throw new ConfusedCFRException("Invalid target");
        targets.set(index, newTarget);
    }

    public void clearSources() {
        sources.clear();
    }
    /*
    public int getIndex() {
        return index;
    }

    public int getSubIndex() {
        return subindex;
    }
    */

    private int getInstrArgByte(int index) {
        return rawData[index];
    }

    @Override
    public List<Op02WithProcessedDataAndRefs> getTargets() {
        return targets;
    }

    @Override
    public List<Op02WithProcessedDataAndRefs> getSources() {
        return sources;
    }

    public void populateStackInfo(StackSim stackSim) {
        StackDelta stackDelta = instr.getStackDelta(rawData, cp, cpEntries, stackSim);
        if (stackDepthBeforeExecution != -1) {
            if (stackSim.getDepth() != stackDepthBeforeExecution) {
                throw new ConfusedCFRException("Invalid stack depths @ " + this + " : expected " + stackSim.getDepth() + " got " + stackDepthBeforeExecution);
            }

            // Merge our current known arguments with these.
            // Since we merge into extant objects, we populate all the downstream
            // dependencies here, and don't need to recurse any futher.

            List<StackEntryHolder> alsoConsumed = ListFactory.newList();
            List<StackEntryHolder> alsoProduced = ListFactory.newList();
            StackSim unusedStack = stackSim.getChange(stackDelta, alsoConsumed, alsoProduced);
            if (alsoConsumed.size() != stackConsumed.size()) {
                throw new ConfusedCFRException("Unexpected stack sizes on merge");
            }
            for (int i = 0; i < stackConsumed.size(); ++i) {
                stackConsumed.get(i).mergeWith(alsoConsumed.get(i));
            }

        } else {
            this.stackDepthBeforeExecution = stackSim.getDepth();
            this.stackDepthAfterExecution = stackDepthBeforeExecution + stackDelta.getChange();

            StackSim newStackSim = stackSim.getChange(stackDelta, stackConsumed, stackProduced);

            for (Op02WithProcessedDataAndRefs target : targets) {
                target.populateStackInfo(newStackSim);
            }
        }
    }

    @Override
    public void dump(Dumper d) {
        d.print("" + index + " : " + instr + "\t Stack:" + stackDepthBeforeExecution + "\t");
        d.print("Consumes:[");
        for (StackEntryHolder stackEntryHolder : stackConsumed) {
            d.print(stackEntryHolder.toString() + " ");
        }
        d.print("] Produces:[");
        for (StackEntryHolder stackEntryHolder : stackProduced) {
            d.print(stackEntryHolder.toString() + " ");
        }
        d.print("]  -> nodes");
        for (Op02WithProcessedDataAndRefs target : targets) {
            d.print(" " + target.index);
        }
        d.print("\n");
    }


    public Statement createStatement(VariableNamer variableNamer) {
        switch (instr) {
            case ALOAD:
            case ILOAD:
            case LLOAD:
                return new Assignment(getStackLValue(0), new FieldExpression(new LocalVariable(getInstrArgByte(0), variableNamer, originalRawOffset)));
            case ALOAD_0:
            case ILOAD_0:
            case LLOAD_0:
            case DLOAD_0:
                return new Assignment(getStackLValue(0), new FieldExpression(new LocalVariable(0, variableNamer, originalRawOffset)));
            case ALOAD_1:
            case ILOAD_1:
            case LLOAD_1:
            case DLOAD_1:
                return new Assignment(getStackLValue(0), new FieldExpression(new LocalVariable(1, variableNamer, originalRawOffset)));
            case ALOAD_2:
            case ILOAD_2:
            case LLOAD_2:
            case DLOAD_2:
                return new Assignment(getStackLValue(0), new FieldExpression(new LocalVariable(2, variableNamer, originalRawOffset)));
            case ALOAD_3:
            case ILOAD_3:
            case LLOAD_3:
            case DLOAD_3:
                return new Assignment(getStackLValue(0), new FieldExpression(new LocalVariable(3, variableNamer, originalRawOffset)));
            case ACONST_NULL:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getNull()));
            case ICONST_M1:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(-1)));
            case ICONST_0:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(0)));
            case ICONST_1:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(1)));
            case ICONST_2:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(2)));
            case ICONST_3:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(3)));
            case ICONST_4:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(4)));
            case ICONST_5:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(5)));
            case LCONST_0:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getLong(0)));
            case LCONST_1:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getLong(1)));
            case BIPUSH:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getInt(rawData[0])));
            case ISTORE:
            case ASTORE:
            case LSTORE:
                return new Assignment(new LocalVariable(getInstrArgByte(0), variableNamer, originalRawOffset), getStackRValue(0));
            case ISTORE_0:
            case ASTORE_0:
            case LSTORE_0:
                return new Assignment(new LocalVariable(0, variableNamer, originalRawOffset), getStackRValue(0));
            case ISTORE_1:
            case ASTORE_1:
            case LSTORE_1:
                return new Assignment(new LocalVariable(1, variableNamer, originalRawOffset), getStackRValue(0));
            case ISTORE_2:
            case ASTORE_2:
            case LSTORE_2:
                return new Assignment(new LocalVariable(2, variableNamer, originalRawOffset), getStackRValue(0));
            case ISTORE_3:
            case ASTORE_3:
            case LSTORE_3:
                return new Assignment(new LocalVariable(3, variableNamer, originalRawOffset), getStackRValue(0));
            case NEW:
                return new Assignment(getStackLValue(0), new NewObject(cp, cpEntries[0]));
            case NEWARRAY:
                return new Assignment(getStackLValue(0), new NewPrimitiveArray(getStackRValue(0), rawData[0]));
            case ANEWARRAY:
                return new Assignment(getStackLValue(0), new NewObjectArray(getStackRValue(0), cp, cpEntries[0]));
            case ARRAYLENGTH:
                return new Assignment(getStackLValue(0), new ArrayLength(getStackRValue(0)));
            case AALOAD:
            case IALOAD:
            case BALOAD:
                return new Assignment(getStackLValue(0), new ArrayIndex(getStackRValue(1), getStackRValue(0)));
            case AASTORE:
            case IASTORE:
            case BASTORE:
                return new Assignment(new ArrayVariable(new ArrayIndex(getStackRValue(2), getStackRValue(1))), getStackRValue(0));
            case LCMP:
            case LSUB:
            case LADD:
            case IADD:
            case ISUB:
            case IDIV:
            case IOR: {
                Expression op = new ArithmeticOperation(getStackRValue(1), getStackRValue(0), ArithOp.getOpFor(instr));
                return new Assignment(getStackLValue(0), op);
            }
            case L2I:
            case I2L:
            case I2S:
                return new Assignment(getStackLValue(0), getStackRValue(0));
            case INSTANCEOF:
                return new Assignment(getStackLValue(0), new InstanceOfExpression(getStackRValue(0), cp, cpEntries[0]));
            case CHECKCAST:
                // Not strictly true, but matches our intermediate form.
                return new Assignment(getStackLValue(0), getStackRValue(0));
            case INVOKESTATIC: {
                StaticFunctionInvokation funcCall = new StaticFunctionInvokation(cp, cpEntries[0], getNStackRValuesAsExpressions(stackConsumed.size()));
                if (stackProduced.size() == 0) {
                    return new ExpressionStatement(funcCall);
                } else {
                    return new Assignment(getStackLValue(0), funcCall);
                }
            }
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKEINTERFACE: {
                ConstantPoolEntryMethodRef function = (ConstantPoolEntryMethodRef) cpEntries[0];
                StackValue object = getStackRValue(stackConsumed.size() - 1);
                MemberFunctionInvokation funcCall = new MemberFunctionInvokation(cp, function, object, getNStackRValuesAsExpressions(stackConsumed.size() - 1));
                if (function.isInitMethod(cp)) {
                    return new ConstructorStatement(funcCall);
                } else {
                    if (stackProduced.size() == 0) {
                        return new ExpressionStatement(funcCall);
                    } else {
                        return new Assignment(getStackLValue(0), funcCall);
                    }
                }
            }
            case RETURN:
                return new ReturnNothingStatement();
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPNE:
            case IF_ICMPEQ:
            case IF_ICMPLE: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(1), getStackRValue(0), CompOp.getOpFor(instr));
                return new IfStatement(conditionalExpression);
            }
            case IFNONNULL: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getNull()), CompOp.NE);
                return new IfStatement(conditionalExpression);
            }
            case IFNULL: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getNull()), CompOp.EQ);
                return new IfStatement(conditionalExpression);
            }
            case IFEQ:
            case IFNE:
            case IFLE:
            case IFLT:
            case IFGT:
            case IFGE: {
                ConditionalExpression conditionalExpression = new ComparisonOperation(getStackRValue(0), new Literal(TypedLiteral.getInt(0)), CompOp.getOpFor(instr));
                return new IfStatement(conditionalExpression);
            }
            case GOTO: {
                return new GotoStatement();
            }
            case ATHROW:
                return new ThrowStatement(getStackRValue(0));
            case IRETURN:
            case ARETURN:
            case LRETURN:
                return new ReturnValueStatement(getStackRValue(0));
            case GETFIELD: {
                Expression fieldExpression = new FieldExpression(new FieldVariable(getStackRValue(0), cp, cpEntries[0]));
                return new Assignment(getStackLValue(0), fieldExpression);
            }
            case GETSTATIC:
                return new Assignment(getStackLValue(0), new FieldExpression(new StaticVariable(cp, cpEntries[0])));
            case PUTSTATIC:
                return new Assignment(new StaticVariable(cp, cpEntries[0]), getStackRValue(0));
            case PUTFIELD:
                return new Assignment(new FieldVariable(getStackRValue(1), cp, cpEntries[0]), getStackRValue(0));
            case DUP: {
                Statement s1 = new Assignment(getStackLValue(0), getStackRValue(0));
                Statement s2 = new Assignment(getStackLValue(1), getStackRValue(0));
                return new CompoundStatement(s1, s2);
            }
            case DUP_X1: {
                Statement s1 = new Assignment(getStackLValue(0), getStackRValue(0));
                Statement s2 = new Assignment(getStackLValue(1), getStackRValue(1));
                Statement s3 = new Assignment(getStackLValue(2), getStackRValue(0));
                return new CompoundStatement(s1, s2, s3);
            }
            case DUP2: {
                if (stackConsumed.get(0).getStackEntry().getType().getComputationCategory() == 2) {
                    Statement s1 = new Assignment(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new Assignment(getStackLValue(1), getStackRValue(0));
                    return new CompoundStatement(s1, s2);
                } else {
                    Statement s1 = new Assignment(getStackLValue(0), getStackRValue(0));
                    Statement s2 = new Assignment(getStackLValue(1), getStackRValue(1));
                    Statement s3 = new Assignment(getStackLValue(2), getStackRValue(0));
                    Statement s4 = new Assignment(getStackLValue(3), getStackRValue(1));
                    return new CompoundStatement(s1, s2, s3, s4);
                }
            }
            case LDC:
            case LDC_W:
            case LDC2_W:
                return new Assignment(getStackLValue(0), new Literal(TypedLiteral.getConstantPoolEntry(cp, cpEntries[0])));
            case FAKE_TRY:
                return new CommentStatement("try {");
            case FAKE_CATCH:
                return new CommentStatement("} catch ... {");
            case POP:
                return new ExpressionStatement(getStackRValue(0));
            case TABLESWITCH:
                return new SwitchStatement(getStackRValue(0), new DecodedTableSwitch(rawData, originalRawOffset));
            case LOOKUPSWITCH:
                return new SwitchStatement(getStackRValue(0), new DecodedLookupSwitch(rawData, originalRawOffset));
            case IINC:
                // Can we have ++ instead?
                return new Assignment(new LocalVariable(rawData[0], variableNamer, originalRawOffset),
                        new ArithmeticOperation(new FieldExpression(new LocalVariable(rawData[0], variableNamer, originalRawOffset)), new Literal(TypedLiteral.getInt(rawData[1])), ArithOp.PLUS));
            default:
                throw new ConfusedCFRException("Not implemented - conversion to statement from " + instr);
        }
    }

    private StackValue getStackRValue(int idx) {
        StackEntryHolder stackEntryHolder = stackConsumed.get(idx);
        StackEntry stackEntry = stackEntryHolder.getStackEntry();
        stackEntry.incrementUsage();
        return new StackValue(stackEntry.getLValue());
    }

    private LValue getStackLValue(int idx) {
        StackEntryHolder stackEntryHolder = stackProduced.get(idx);
        StackEntry stackEntry = stackEntryHolder.getStackEntry();
        return stackEntry.getLValue();
    }

    private List<Expression> getNStackRValuesAsExpressions(int count) {
        List<Expression> res = ListFactory.newList();
        for (int i = 0; i < count; ++i) {
            res.add(getStackRValue(i));
        }
        return res;
    }

    @Override
    public String toString() {
        return "" + index + " : " + instr;
    }
}
