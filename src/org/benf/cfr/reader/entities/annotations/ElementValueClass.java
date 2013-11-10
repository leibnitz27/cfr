package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueClass implements ElementValue {
    private final String className;

    public ElementValueClass(String className) {
        this.className = className;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(className);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }
}
