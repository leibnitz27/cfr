package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.util.DecompilerComments;

public interface TypePathPart {
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it, DecompilerComments comments);
}
