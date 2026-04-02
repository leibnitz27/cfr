package org.benf.cfr.reader.bytecode.analysis.parse.pattern;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Pattern;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class RecordPattern implements Pattern {
    LValue outer;
    List<LValue> params;

    public RecordPattern(LValue outer, List<LValue> params) {
        this.outer = outer;
        this.params = params;
    }

    @Override
    public InferredJavaType getInferredJavaType() {
        return outer.getInferredJavaType();
    }

    @Override
    public Dumper dump(Dumper d, boolean defines) {
        d.dump(outer.getInferredJavaType().getJavaTypeInstance());
        d.print('(');
        boolean first = true;
        for (LValue lv : params) {
            first = StringUtils.comma(first, d);
            LValue.Creation.dump(d, lv);
        }
        d.print(')');
        return d;
    }

    @Override
    public Dumper dump(Dumper d) {
        return dump(d, true);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(outer);
        for (LValue lv : params) {
            collector.collectFrom(lv);
        }
    }
}
