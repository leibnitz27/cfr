package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.ConfusedCFRException;

import java.util.Set;

public enum ArithOp {
    LCMP("LCMP", true, Precedence.WEAKEST),
    DCMPL("DCMPL", true, Precedence.WEAKEST),
    DCMPG("DCMPG", true, Precedence.WEAKEST),
    FCMPL("FCMPL", true, Precedence.WEAKEST),
    FCMPG("FCMPG", true, Precedence.WEAKEST),
    PLUS("+", false, Precedence.ADD_SUB),
    MINUS("-", false, Precedence.ADD_SUB),
    MULTIPLY("*", false, Precedence.MUL_DIV_MOD),
    DIVIDE("/", false, Precedence.MUL_DIV_MOD),
    REM("%", false, Precedence.MUL_DIV_MOD),
    OR("|", false, Precedence.BIT_OR),
    AND("&", false, Precedence.BIT_AND),
    SHR(">>", false, Precedence.BITWISE_SHIFT),
    SHL("<<", false, Precedence.BITWISE_SHIFT),
    SHRU(">>>", false, Precedence.BITWISE_SHIFT),
    XOR("^", false, Precedence.BIT_XOR),
    NEG("~", false, Precedence.UNARY_OTHER);

    private final String showAs;
    private final boolean temporary;
    private final Precedence precedence;

    ArithOp(String showAs, boolean temporary, Precedence precedence) {
        this.showAs = showAs;
        this.temporary = temporary;
        this.precedence = precedence;
    }

    public String getShowAs() {
        return showAs;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public Precedence getPrecedence() {
        return precedence;
    }

    public static ArithOp getOpFor(JVMInstr instr) {
        switch (instr) {
            case LCMP:
                return LCMP;
            case DCMPG:
                return DCMPG;
            case DCMPL:
                return DCMPL;
            case FCMPG:
                return FCMPG;
            case FCMPL:
                return FCMPL;
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
                return MINUS;
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
                return MULTIPLY;
            case IADD:
            case LADD:
            case FADD:
            case DADD:
                return PLUS;
            case LDIV:
            case IDIV:
            case FDIV:
            case DDIV:
                return DIVIDE;
            case LOR:
            case IOR:
                return OR;
            case LAND:
            case IAND:
                return AND;
            case IREM:
            case LREM:
            case FREM:
            case DREM:
                return REM;
            case ISHR:
            case LSHR:
                return SHR;
            case IUSHR:
            case LUSHR:
                return SHRU;
            case ISHL:
            case LSHL:
                return SHL;
            case IXOR:
            case LXOR:
                return XOR;
            default:
                throw new ConfusedCFRException("Don't know arith op for " + instr);
        }
    }

    public boolean canThrow(InferredJavaType inferredJavaType, ExceptionCheck caught, Set<? extends JavaTypeInstance> instances) {
        StackType stackType = inferredJavaType.getRawType().getStackType();
        switch (stackType) {
            case DOUBLE:
            case FLOAT:
            case INT:
            case LONG:
                if (this != DIVIDE) return false;
                break;
        }
        return caught.checkAgainst(instances);
    }
}
