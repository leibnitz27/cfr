package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssigmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 */
/*
 * Wraps a local, a static or an instance field.
 */
public class FieldExpression implements LValueExpression {
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
    public Expression replaceSingleUsageLValues(LValueAssigmentCollector lValueAssigmentCollector, SSAIdentifiers ssaIdentifiers) {
        fieldVariable = fieldVariable.replaceSingleUsageLValues(lValueAssigmentCollector, ssaIdentifiers);
        return this;
    }

    @Override
    public String toString() {
        return fieldVariable.toString();
    }

    @Override
    public LValue getLValue() {
        return fieldVariable;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(fieldVariable);
    }

}
