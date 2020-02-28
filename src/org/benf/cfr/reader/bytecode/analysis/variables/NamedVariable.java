package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

public interface NamedVariable extends Dumpable {
    void forceName(String name);

    String getStringName();

    boolean isGoodName();

    @Override
    Dumper dump(Dumper d);

    Dumper dump(Dumper d, boolean defines);

    Dumper dumpParameter(Dumper d, MethodPrototype methodPrototype, int index, boolean defines);
}
