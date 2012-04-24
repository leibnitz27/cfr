package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 */
/*
 * Wraps a local, a static or an instance field.
 */
public class FieldExpression implements Expression {
    private LValue fieldVariable;

    public FieldExpression(LValue fieldVariable) {
        this.fieldVariable = fieldVariable;
    }

    @Override
    public boolean isSimple() {
        // A field expression is 'simple' only if it's final.
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueCollector lValueCollector) {
        fieldVariable = fieldVariable.replaceSingleUsageLValues(lValueCollector);
        return this;
    }

    @Override
    public String toString() {
        return fieldVariable.toString();
    }

}
