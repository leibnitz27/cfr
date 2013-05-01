package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueConst implements ElementValue {
    private final TypedLiteral value;

    public ElementValueConst(TypedLiteral value) {
        this.value = value;
    }

    @Override
    public void dump(Dumper d) {
        d.print(value.toString());
    }
}
