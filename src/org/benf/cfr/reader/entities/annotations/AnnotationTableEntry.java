package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Map;
import java.util.SortedMap;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 06:36
 */
public class AnnotationTableEntry implements TypeUsageCollectable {
    private final JavaTypeInstance clazz;
    // Sorted map to make ordering predictable.
    private final Map<String, ElementValue> elementValueMap;

    public AnnotationTableEntry(JavaTypeInstance clazz, Map<String, ElementValue> elementValueMap) {
        this.clazz = clazz;
        this.elementValueMap = elementValueMap;
    }

    public Dumper dump(Dumper d) {
        d.print('@').dump(clazz);
        if (elementValueMap != null && !elementValueMap.isEmpty()) {
            d.print('(');
            boolean first = true;
            for (Map.Entry<String, ElementValue> elementValueEntry : elementValueMap.entrySet()) {
                first = CommaHelp.comma(first, d);
                d.print(elementValueEntry.getKey()).print('=');
                elementValueEntry.getValue().dump(d);
            }
            d.print(')');
        }
        return d;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(clazz);
        if (elementValueMap != null) {
            for (ElementValue elementValue : elementValueMap.values()) {
                elementValue.collectTypeUsages(collector);
            }
        }
    }
}
