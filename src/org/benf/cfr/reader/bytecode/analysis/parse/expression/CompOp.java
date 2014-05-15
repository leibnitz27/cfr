package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.util.ConfusedCFRException;

public enum CompOp {
    LT("<", Precedence.REL_CMP_INSTANCEOF),
    GT(">", Precedence.REL_CMP_INSTANCEOF),
    LTE("<=", Precedence.REL_CMP_INSTANCEOF),
    GTE(">=", Precedence.REL_CMP_INSTANCEOF),
    EQ("==", Precedence.REL_EQ),
    NE("!=", Precedence.REL_EQ);


    private final String showAs;
    private final Precedence precedence;

    private CompOp(String showAs, Precedence precedence) {
        this.showAs = showAs;
        this.precedence = precedence;
    }

    public String getShowAs() {
        return showAs;
    }

    public Precedence getPrecedence() {
        return precedence;
    }

    public CompOp getInverted() {
        switch (this) {
            case LT:
                return GTE;
            case GT:
                return LTE;
            case GTE:
                return LT;
            case LTE:
                return GT;
            case EQ:
                return NE;
            case NE:
                return EQ;
            default:
                throw new ConfusedCFRException("Can't invert CompOp " + this);
        }
    }


    public static CompOp getOpFor(JVMInstr instr) {
        switch (instr) {
            case IF_ICMPEQ:
            case IF_ACMPEQ:
                return EQ;
            case IF_ICMPLT:
                return LT;
            case IF_ICMPGE:
                return GTE;
            case IF_ICMPGT:
                return GT;
            case IF_ICMPNE:
            case IF_ACMPNE:
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
