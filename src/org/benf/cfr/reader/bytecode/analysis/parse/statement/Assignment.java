package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.CreationCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 17:57
 * To change this template use File | Settings | File Templates.
 */
public class Assignment extends AbstractStatement {
    private LValue lvalue;
    private Expression rvalue;

    public Assignment(LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print(lvalue.toString() + " = " + rvalue.toString() + ";\n");
    }

    @Override
    public void getLValueEquivalences(LValueCollector lValueCollector) {
        lvalue.determineLValueEquivalence(rvalue, this.getContainer(), lValueCollector);
    }

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
        creationCollector.collectCreation(lvalue, rvalue, this.getContainer());
    }

    @Override
    public LValue getCreatedLValue() {
        return lvalue;
    }

    @Override
    public Expression getRValue() {
        return rvalue;
    }

    @Override
    public void replaceSingleUsageLValues(LValueCollector lValueCollector) {
        lvalue = lvalue.replaceSingleUsageLValues(lValueCollector);
        rvalue = rvalue.replaceSingleUsageLValues(lValueCollector);
    }

}
