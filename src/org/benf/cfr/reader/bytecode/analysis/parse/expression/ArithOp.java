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
    PLUS("+"),
    MINUS("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    REM("%"),
    OR("|"),
    AND("&");

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
            case ISUB:
            case LSUB:
                return MINUS;
            case IMUL:
            case LMUL:
                return MULTIPLY;
            case IADD:
            case LADD:
                return PLUS;
            case LDIV:
            case IDIV:
                return DIVIDE;
            case LOR:
            case IOR:
                return OR;
            case LAND:
            case IAND:
                return AND;
            case IREM:
                return REM;
            default:
                throw new ConfusedCFRException("Don't know arith op for " + instr);
        }
    }
}
