package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public class FieldVariable extends AbstractLValue {

    private Expression object;
    private final ConstantPoolEntryFieldRef field;

    private static InferredJavaType getFieldType(ConstantPoolEntry fieldentry, ClassFile classFile, ConstantPool cp) {
        ConstantPoolEntryFieldRef fieldRef = (ConstantPoolEntryFieldRef) fieldentry;
        String name = fieldRef.getLocalName();
        try {
            Field field = classFile.getFieldByName(name);
            return new InferredJavaType(field.getJavaTypeInstance(cp), InferredJavaType.Source.FIELD);
        } catch (NoSuchFieldException ignore) {
            return new InferredJavaType(fieldRef.getJavaTypeInstance(), InferredJavaType.Source.FIELD);
        }
    }

    public FieldVariable(Expression object, ClassFile classFile, ConstantPool cp, ConstantPoolEntry field) {
        super(getFieldType(field, classFile, cp));
        this.object = object;
        this.field = (ConstantPoolEntryFieldRef) field;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    private String getFieldName() {
        return field.getLocalName();
    }

    @Override
    public Dumper dump(Dumper d) {
        return object.dump(d).print(".").print(getFieldName());
    }

    @Override
    public SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
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

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;

        if (!(o instanceof FieldVariable)) return false;
        FieldVariable other = (FieldVariable) o;

        if (!object.equals(other.object)) return false;
        if (!getFieldName().equals(other.getFieldName())) return false;
        return true;
    }
}
