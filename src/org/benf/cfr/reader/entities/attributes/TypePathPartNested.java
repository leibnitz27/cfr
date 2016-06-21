package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;

public class TypePathPartNested implements TypePathPart {
    public static TypePathPartNested INSTANCE = new TypePathPartNested();

    private TypePathPartNested() {
    }

    @Override
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it) {
        return it.moveNested();
    }
}
