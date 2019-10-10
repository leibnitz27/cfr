package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

public class UnstructuredGoto extends AbstractUnStructuredStatement {

    public UnstructuredGoto() {
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("** GOTO " + getContainer().getTargetLabel(0)).newln();
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }
}
