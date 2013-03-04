package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public class FieldVariable extends AbstractLValue {

    private Expression object;
    private final ConstantPool cp;
    private final ConstantPoolEntryFieldRef field;

    private static InferredJavaType getFieldType(ConstantPoolEntry fieldentry, ClassFile classFile, ConstantPool cp) {
        ConstantPoolEntryFieldRef fieldRef = (ConstantPoolEntryFieldRef) fieldentry;
        String name = fieldRef.getLocalName(cp);
        try {
            Field field = classFile.getFieldByName(name);
            return new InferredJavaType(field.getJavaTypeInstance(cp), InferredJavaType.Source.FIELD);
        } catch (NoSuchFieldException _) {
            return new InferredJavaType(fieldRef.getJavaTypeInstance(cp), InferredJavaType.Source.FIELD);
        }
    }

    public FieldVariable(Expression object, ClassFile classFile, ConstantPool cp, ConstantPoolEntry field) {
        super(getFieldType(field, classFile, cp));
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
    public void determineLValueEquivalence(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }
}
