package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 26/03/2014
 * Time: 17:26
 */
public interface ExceptionCheck {
    boolean checkAgainst(Set<? extends JavaTypeInstance> thrown);

    // Might this throw in a way which means it can't be moved into the exception block?
    boolean checkAgainst(AbstractFunctionInvokation functionInvokation);

    boolean checkAgainstException(Expression expression);

    boolean mightCatchUnchecked();
}
