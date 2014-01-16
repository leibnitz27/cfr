package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueEnum implements ElementValue {
    private final JavaTypeInstance type;
    private final String valueName;

    public ElementValueEnum(JavaTypeInstance type, String valueName) {
        this.type = type;
        this.valueName = valueName;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.dump(type).print('.').print(valueName);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(type);
    }
}
