package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

/**
 * Created:
 * User: lee
 * Date: 25/04/2012
 */
public interface LValueExpression extends Expression {
    LValue getLValue();
}
