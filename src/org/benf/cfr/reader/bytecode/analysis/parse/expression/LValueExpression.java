package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
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
public class LValueExpression extends AbstractExpression {
    private LValue lValue;

    public LValueExpression(LValue lValue) {
        super(lValue.getInferredJavaType());
        this.lValue = lValue;
    }

    @Override
    public boolean isSimple() {
        // A field expression is 'simple' only if it's final.
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        if (lValueRewriter.explicitlyReplaceThisLValue(lValue)) {
            return lValueRewriter.getLValueReplacement(lValue, ssaIdentifiers, statementContainer);
        } else {
            lValue = lValue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
            return this;
        }
    }

    @Override
    public String toString() {
        return lValue.toString();
    }

    public LValue getLValue() {
        return lValue;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(lValue);
    }

}
