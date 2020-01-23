package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Collection;
import java.util.Map;

public class AttributeMap implements TypeUsageCollectable {
    private final Map<String, Attribute> attributes;

    public AttributeMap(Collection<Attribute> tmpAttributes) {
        attributes = MapFactory.newMap();
        for (Attribute a : tmpAttributes) {
            attributes.put(a.getRawName(), a);
        }
    }

    public <T extends Attribute> T getByName(String name) {
        Attribute attribute = attributes.get(name);
        if (attribute == null) return null;
        // There's no reason to be getting an unknown attribute here.  This means we
        // tried to fetch a well known name.
        if (attribute instanceof AttributeUnknown) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T tmp = (T) attribute;
        return tmp;
    }

    public boolean containsKey(String attributeName) {
        return attributes.containsKey(attributeName);
    }

    public void clear() {
        attributes.clear();
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (Attribute attribute : attributes.values()) {
            attribute.collectTypeUsages(collector);
        }
    }

    public boolean any(String ... attributeNames) {
        for (String name : attributeNames) {
            if (attributes.containsKey(name)) return true;
        }
        return false;
    }
}
