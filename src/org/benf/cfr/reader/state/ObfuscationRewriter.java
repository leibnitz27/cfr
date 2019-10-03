package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

public interface ObfuscationRewriter {
    Dumper wrap(Dumper d);

    JavaTypeInstance get(JavaTypeInstance t);
}
