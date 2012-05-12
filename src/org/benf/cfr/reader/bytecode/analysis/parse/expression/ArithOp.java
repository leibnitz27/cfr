package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.util.ConfusedCFRException;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 06:34
 * To change this template use File | Settings | File Templates.
 */
public enum ArithOp {
    LCMP("LCMP"),
    DCMPL("DCMPL"),
    DCMPG("DCMPG"),
    FCMPL("FCMPL"),
    FCMPG("FCMPG"),
    PLUS("+"),
    MINUS("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    REM("%"),
    OR("|"),
    AND("&"),
    SHR(">>"),
    SHL("<<"),
    XOR("^");

    private final String showAs;

    private ArithOp(String showAs) {
        this.showAs = showAs;
    }

    public String getShowAs() {
        return showAs;
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
            case IUSHR: // TODO : WRONG
            case LUSHR: // TODO : WRONG
                return SHR;
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
