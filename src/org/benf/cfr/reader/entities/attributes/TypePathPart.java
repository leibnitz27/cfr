package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;

public interface TypePathPart {
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it);
}
