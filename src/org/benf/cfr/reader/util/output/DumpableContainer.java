package org.benf.cfr.reader.util.output;

import java.util.List;

public class DumpableContainer implements Dumpable {
    private final List<? extends Dumpable> dumpables;

    public DumpableContainer(List<? extends Dumpable> dumpables) {
        this.dumpables = dumpables;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        for (Dumpable dumpable : dumpables) {
            dumpable.dump(dumper);
        }
        return dumper;
    }
}
