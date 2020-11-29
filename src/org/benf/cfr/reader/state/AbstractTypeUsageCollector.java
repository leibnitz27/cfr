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
    public void collectFrom(Object collectValue) {
        if (collectValue == null) {
            return;
        } else if (collectValue instanceof TypeUsageCollectable) {
            ((TypeUsageCollectable) collectValue).collectTypeUsages(this);
        } else if (collectValue instanceof Collection) {
            Collection<? extends TypeUsageCollectable> collectables = (Collection<? extends TypeUsageCollectable>) collectValue;
            for (TypeUsageCollectable collectable : collectables) {
                if (collectable != null) {
                    collectable.collectTypeUsages(this);
                }
            }
        } else {
            throw new UnsupportedOperationException("Unsupported type:" + collectValue.getClass());
        }
    }
}
