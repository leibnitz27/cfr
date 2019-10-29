package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public interface ObfuscationRewriter {
    Dumper wrap(Dumper d);

    JavaTypeInstance get(JavaTypeInstance t);

    List<JavaTypeInstance> get(List<JavaTypeInstance> types);
}
