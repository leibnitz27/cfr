package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.util.DecompilerComments;

public class TypePathPartArray implements TypePathPart {
    public static TypePathPartArray INSTANCE = new TypePathPartArray();

    private TypePathPartArray() {
    }

    @Override
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it, DecompilerComments comments) {
        return it.moveArray(comments);
    }
}
