package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;

public class TypePathPartBound implements TypePathPart {
    public static TypePathPartBound INSTANCE = new TypePathPartBound();

    private TypePathPartBound() {
    }

    @Override
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it) {
        return it.moveBound();
    }
}
