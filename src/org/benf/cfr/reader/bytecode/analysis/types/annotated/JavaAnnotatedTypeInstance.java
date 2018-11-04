package org.benf.cfr.reader.bytecode.analysis.types.annotated;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.util.output.Dumpable;

public interface JavaAnnotatedTypeInstance extends Dumpable {
    JavaAnnotatedTypeIterator pathIterator();
}
