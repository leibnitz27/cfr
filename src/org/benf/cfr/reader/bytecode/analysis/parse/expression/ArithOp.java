package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 06:34
 * To change this template use File | Settings | File Templates.
 */
public enum ArithOp {
    LCMP("LCMP", true, -1),
    DCMPL("DCMPL", true, -1),
    DCMPG("DCMPG", true, -1),
    FCMPL("FCMPL", true, -1),
    FCMPG("FCMPG", true, -1),
    PLUS("+", false, 0),
    MINUS("-", false, 0),
    MULTIPLY("*", false, 1),
    DIVIDE("/", false, 2),
    REM("%", false, -1),
    OR("|", false, -1),
    AND("&", false, -1),
    SHR(">>", false, -1),
    SHL("<<", false, -1),
    SHRU(">>>", false, -1),
    XOR("^", false, -1);

    private final String showAs;
    private final boolean temporary;
    private final int precedence;

    private ArithOp(String showAs, boolean temporary, int precedence) {
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

    public int getPrecedence() {
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
}
