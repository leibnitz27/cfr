package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.CreationCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssigmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredConstruction;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * <p/>
 * This is a temporary statement - it should be replaced with an Assignment of a ConstructorInvokation
 */
public class ConstructorStatement extends AbstractStatement {
    private MemberFunctionInvokation invokation;

    public ConstructorStatement(MemberFunctionInvokation construction) {
        this.invokation = construction;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print(invokation.toString() + "; // <-- constructor of (" + invokation.getObject() + ")\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueAssigmentCollector lValueAssigmentCollector, SSAIdentifiers ssaIdentifiers) {
        // can't ever change, but its arguments can.
        invokation.replaceSingleUsageLValues(lValueAssigmentCollector, ssaIdentifiers);
    }

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
        Expression object = invokation.getObject();
        creationCollector.collectConstruction(object, invokation, this.getContainer());
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredConstruction(invokation);
    }
}
