package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

public class ElementValueConst implements ElementValue {
    private final TypedLiteral value;

    public ElementValueConst(TypedLiteral value) {
        this.value = value;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.dump(value);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }

}
