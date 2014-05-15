package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

public class FormalTypeParameter implements Dumpable, TypeUsageCollectable {
    String name;
    JavaTypeInstance classBound;
    JavaTypeInstance interfaceBound;

    public FormalTypeParameter(String name, JavaTypeInstance classBound, JavaTypeInstance interfaceBound) {
        this.name = name;
        this.classBound = classBound;
        this.interfaceBound = interfaceBound;
    }

    public String getName() {
        return name;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(classBound);
        collector.collect(interfaceBound);
    }

    @Override
    public Dumper dump(Dumper d) {
        JavaTypeInstance dispInterface = classBound == null ? interfaceBound : classBound;
        d.print(name);
        if (dispInterface != null) {
            if (!"java.lang.Object".equals(dispInterface.getRawName())) {
                d.print(" extends ").dump(dispInterface);
            }
        }
        return d;
    }

    @Override
    public String toString() {
        throw new IllegalStateException();
    }
}
