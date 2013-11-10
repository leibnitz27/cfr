package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

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

    @Override
    public Dumper dump(Dumper d) {
        return d.print(className).print('.').print(valueName);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }
}
