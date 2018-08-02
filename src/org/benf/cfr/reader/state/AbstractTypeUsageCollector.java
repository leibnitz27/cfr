package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.TypeUsageCollectable;

import java.util.Collection;

public abstract class AbstractTypeUsageCollector implements TypeUsageCollector {
    @Override
    public void collect(Collection<? extends JavaTypeInstance> types) {
        if (types == null) return;
        for (JavaTypeInstance type : types) collect(type);
    }

    @Override
    public void collectFrom(TypeUsageCollectable collectable) {
        if (collectable != null) collectable.collectTypeUsages(this);
    }

    @Override
    public void collectFrom(Collection<? extends TypeUsageCollectable> collectables) {
        if (collectables != null) {
            for (TypeUsageCollectable collectable : collectables) {
                if (collectable != null) collectable.collectTypeUsages(this);
            }
        }
    }
}
