package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;

public class StdIODumper extends StreamDumper {
    public StdIODumper(TypeUsageInformation typeUsageInformation) {
        super(typeUsageInformation);
    }

    @Override
    protected void write(String s) {
        System.out.print(s);
    }

    @Override
    public void addSummaryError(Method method, String s) {
    }

    @Override
    public void close() {
    }
}
