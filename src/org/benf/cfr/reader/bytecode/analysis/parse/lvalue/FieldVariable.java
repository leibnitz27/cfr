package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryFieldRef;
import org.benf.cfr.reader.util.ConfusedCFRException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public class FieldVariable implements LValue {

    private Expression object;
    private final ConstantPool cp;
    private final ConstantPoolEntryFieldRef field;

    public FieldVariable(Expression object, ConstantPool cp, ConstantPoolEntry field) {
        this.object = object;
        this.field = (ConstantPoolEntryFieldRef) field;
        this.cp = cp;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    @Override
    public String toString() {
        return "" + object + "." + field.getLocalName(cp);
    }

    @Override
    public SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public void determineLValueEquivalence(Expression assignedTo, StatementContainer statementContainer, LValueCollector lValueCollector) {
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        object = object.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
        return this;
    }

}
