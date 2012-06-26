package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:53
 * To change this template use File | Settings | File Templates.
 */
public class StackValue implements Expression {
    private final StackSSALabel stackValue;

    public StackValue(StackSSALabel stackValue) {
        this.stackValue = stackValue;
    }

    @Override
    public String toString() {
        return stackValue.toString();
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        Expression replaceMeWith = lValueRewriter.getLValueReplacement(stackValue, ssaIdentifiers, statementContainer);
        if (replaceMeWith != null) return replaceMeWith;
        return this;
    }

    public StackSSALabel getStackValue() {
        return stackValue;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(stackValue);
    }

}
