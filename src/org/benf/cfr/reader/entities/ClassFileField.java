package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/05/2013
 * Time: 17:26
 */
public class ClassFileField {
    private final Field field;
    /*
     * Because we might lift this, we split it out from the field.
     */
    private Expression initialValue;
    private boolean isHidden;
    private String overriddenName;

    public ClassFileField(Field field) {
        this.field = field;
        TypedLiteral constantValue = field.getConstantValue();
        initialValue = constantValue == null ? null : new Literal(constantValue);
        isHidden = false;
    }

    public Field getField() {
        return field;
    }

    public Expression getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(Expression rValue) {
        this.initialValue = rValue;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void markHidden() {
        isHidden = true;
    }

    public void overrideName(String override) {
        overriddenName = override;
    }

    public String getFieldName() {
        if (overriddenName != null) return overriddenName;
        return field.getFieldName();
    }

    public void dump(Dumper d, ConstantPool cp) {
        field.dump(d, getFieldName(), cp);
        if (initialValue != null) {
            d.print(" = ").dump(initialValue);
        }
        d.endCodeln();
    }
}
