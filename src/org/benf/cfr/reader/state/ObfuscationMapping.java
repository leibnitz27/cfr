package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.functors.UnaryFunction;

public interface ObfuscationMapping {
    JavaTypeInstance get(JavaTypeInstance type);

    UnaryFunction<JavaTypeInstance, JavaTypeInstance> getter();
}
