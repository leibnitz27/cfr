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
public enum CompOp {
    LT("<"),
    GT(">"),
    LTE("<="),
    GTE(">="),
    EQ("=="),
    NE("!=");


    private final String showAs;

    private CompOp(String showAs) {
        this.showAs = showAs;
    }

    public String getShowAs() {
        return showAs;
    }

    public static CompOp getOpFor(JVMInstr instr) {
        switch (instr) {
            case IF_ICMPEQ:
                return EQ;
            case IF_ICMPLT:
                return LT;
            case IF_ICMPGE:
                return GTE;
            case IF_ICMPGT:
                return GT;
            case IF_ICMPNE:
                return NE;
            case IF_ICMPLE:
                return LTE;
            case IFEQ:
                return EQ;
            case IFNE:
                return NE;
            case IFLE:
                return LTE;
            case IFLT:
                return LT;
            case IFGE:
                return GTE;
            case IFGT:
                return GT;
            default:
                throw new ConfusedCFRException("Don't know comparison op for " + instr);
        }
    }
}
