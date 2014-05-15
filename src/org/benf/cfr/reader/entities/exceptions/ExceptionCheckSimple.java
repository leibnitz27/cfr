package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

import java.util.Set;

public class ExceptionCheckSimple implements ExceptionCheck {
    public static final ExceptionCheck INSTANCE = new ExceptionCheckSimple();

    private ExceptionCheckSimple() {
    }

    @Override
    public boolean checkAgainst(Set<? extends JavaTypeInstance> thrown) {
        return true;
    }

    @Override
    public boolean checkAgainst(AbstractFunctionInvokation functionInvokation) {
        return true;
    }

    @Override
    public boolean checkAgainstException(Expression expression) {
        return true;
    }

    @Override
    public boolean mightCatchUnchecked() {
        return true;
    }
}
