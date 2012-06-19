package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.CreationCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
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
        dumper.print(this.toString() + ";\n");
    }

    @Override
    public String toString() {
        return (lvalue.toString() + " = " + rvalue.toString());
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
    public SSAIdentifiers collectLocallyMutatedVariables(SSAIdentifierFactory ssaIdentifierFactory) {
        return lvalue.collectVariableMutation(ssaIdentifierFactory);
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
    public void replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        lvalue = lvalue.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
        rvalue = rvalue.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredAssignment(lvalue, rvalue);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Assignment)) return false;

        Assignment other = (Assignment) o;
        return lvalue.equals(other.lvalue) && rvalue.equals(other.rvalue);
    }
}
