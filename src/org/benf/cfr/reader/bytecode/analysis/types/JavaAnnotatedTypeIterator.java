package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;

// Implementations of this which end up in the wrong place return themselves, which is wrong, but tolerable.
public interface JavaAnnotatedTypeIterator {
    JavaAnnotatedTypeIterator moveArray();
    JavaAnnotatedTypeIterator moveBound();
    JavaAnnotatedTypeIterator moveNested();
    JavaAnnotatedTypeIterator moveParameterized(int index);
    void apply(AnnotationTableTypeEntry entry);
}
