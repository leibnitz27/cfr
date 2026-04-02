package org.benf.cfr.reader.bytecode.analysis.parse.pattern;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Pattern;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

public class TypePattern implements Pattern {
    private final LValue lValue;

    public TypePattern(LValue lValue) {
        this.lValue = lValue;
    }

    @Override
    public InferredJavaType getInferredJavaType() {
        return lValue.getInferredJavaType();
    }

    @Override
    public Dumper dump(Dumper d, boolean defines) {
        return LValue.Creation.dump(d, lValue);
    }

    @Override
    public Dumper dump(Dumper d) {
        return dump(d, true);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(lValue);
    }
}
