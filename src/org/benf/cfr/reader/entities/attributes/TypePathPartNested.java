package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.util.DecompilerComments;

public class TypePathPartNested implements TypePathPart {
    public static final TypePathPartNested INSTANCE = new TypePathPartNested();

    private TypePathPartNested() {
    }

    @Override
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it, DecompilerComments comments) {
        return it.moveNested(comments);
    }
}
