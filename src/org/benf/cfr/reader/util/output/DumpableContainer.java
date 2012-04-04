package org.benf.cfr.reader.util.output;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/03/2012
 * Time: 06:22
 * To change this template use File | Settings | File Templates.
 */
public class DumpableContainer implements Dumpable {
    private final List<? extends Dumpable> dumpables;
    
    public DumpableContainer(List<? extends Dumpable> dumpables) {
        this.dumpables = dumpables;
    }

    @Override
    public void dump(Dumper dumper) {
        for (Dumpable dumpable : dumpables) {
            dumpable.dump(dumper);
        }
    }
}
