package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.TypeUsageCollectable;

import java.util.Collection;

public interface TypeUsageCollector {
    void collectRefType(JavaRefTypeInstance type);

    void collect(JavaTypeInstance type);

    void collect(Collection<? extends JavaTypeInstance> types);

    // Explicitly named helper to allow J8's less lenient bridging.
    void collectFromT(TypeUsageCollectable collectable);

    void collectFrom(TypeUsageCollectable collectable);

    void collectFrom(Collection<? extends TypeUsageCollectable> collectables);

    TypeUsageInformation getTypeUsageInformation();

    boolean isStatementRecursive();
}
