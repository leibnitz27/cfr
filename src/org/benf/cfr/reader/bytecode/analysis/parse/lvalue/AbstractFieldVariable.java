package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryFieldRef;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;

public abstract class AbstractFieldVariable extends AbstractLValue {

    private final ClassFileField classFileField;
    private final String failureName; // if we can't get the classfileField.
    private final JavaTypeInstance owningClass;

    public AbstractFieldVariable(ConstantPoolEntry field) {
        super(getFieldType((ConstantPoolEntryFieldRef) field));
        ConstantPoolEntryFieldRef fieldRef = (ConstantPoolEntryFieldRef) field;
        this.classFileField = getField(fieldRef);
        this.failureName = fieldRef.getLocalName();
        this.owningClass = fieldRef.getClassEntry().getTypeInstance();
    }

    protected AbstractFieldVariable(AbstractFieldVariable other) {
        super(other.getInferredJavaType());
        this.classFileField = other.classFileField;
        this.failureName = other.failureName;
        this.owningClass = other.owningClass;
    }

    protected AbstractFieldVariable(InferredJavaType type, JavaTypeInstance clazz, String varName) {
        super(type);
        this.classFileField = null;
        this.owningClass = clazz;
        this.failureName = varName;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        super.collectTypeUsages(collector);
        if (classFileField != null) collector.collect(classFileField.getField().getJavaTypeInstance());
        collector.collect(owningClass);
    }

    @Override
    public void markFinal() {

    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    public JavaTypeInstance getOwningClassType() {
        return owningClass;
    }

    public String getFieldName() {
        if (classFileField == null) {
            return failureName;
        }
        return classFileField.getFieldName();
    }

    public ClassFileField getClassFileField() {
        return classFileField;
    }

    @Override
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }


    public static ClassFileField getField(ConstantPoolEntryFieldRef fieldRef) {
        String name = fieldRef.getLocalName();
        JavaRefTypeInstance ref = (JavaRefTypeInstance) fieldRef.getClassEntry().getTypeInstance();
        try {
            ClassFile classFile = ref.getClassFile();
            if (classFile == null) return null;

            ClassFileField field = classFile.getFieldByName(name, fieldRef.getJavaTypeInstance());
            return field;
        } catch (NoSuchFieldException ignore) {
        } catch (CannotLoadClassException ignore) {
        }
        return null;
    }


    static InferredJavaType getFieldType(ConstantPoolEntryFieldRef fieldRef) {
        String name = fieldRef.getLocalName();
        JavaRefTypeInstance ref = (JavaRefTypeInstance) fieldRef.getClassEntry().getTypeInstance();
        try {
            ClassFile classFile = ref.getClassFile();
            if (classFile != null) {
                // this now seems rather pointless, as it's passing the type to GET the type!
                Field field = classFile.getFieldByName(name, fieldRef.getJavaTypeInstance()).getField();
                return new InferredJavaType(field.getJavaTypeInstance(), InferredJavaType.Source.FIELD);
            }
        } catch (CannotLoadClassException e) {
        } catch (NoSuchFieldException ignore) {
        }
        return new InferredJavaType(fieldRef.getJavaTypeInstance(), InferredJavaType.Source.FIELD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractFieldVariable)) return false;

        AbstractFieldVariable that = (AbstractFieldVariable) o;

        if (!getFieldName().equals(that.getFieldName())) return false;
        if (owningClass != null ? !owningClass.equals(that.owningClass) : that.owningClass != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = getFieldName().hashCode();
        result = 31 * result + (owningClass != null ? owningClass.hashCode() : 0);
        return result;
    }
}
