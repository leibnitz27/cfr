package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.util.DecompilerComments;

public class TypePathPartBound implements TypePathPart {
    public static TypePathPartBound INSTANCE = new TypePathPartBound();

    private TypePathPartBound() {
    }

    @Override
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it, DecompilerComments comments) {
        return it.moveBound(comments);
    }
}
