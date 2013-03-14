package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueEnum implements ElementValue {
    private final String className;
    private final String valueName;

    public ElementValueEnum(String className, String valueName) {
        this.className = className;
        this.valueName = valueName;
    }
}
