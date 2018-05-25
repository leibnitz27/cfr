package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

public interface ProgressDumper {
    void analysingType(JavaTypeInstance type);
}
