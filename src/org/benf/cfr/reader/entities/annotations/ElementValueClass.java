package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueClass implements ElementValue {
    private final JavaTypeInstance classType;

    public ElementValueClass(JavaTypeInstance classType) {
        this.classType = classType;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.dump(classType).print(".class");
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(classType);
    }
}
