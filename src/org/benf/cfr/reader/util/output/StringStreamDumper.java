package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;

public class StringStreamDumper extends StreamDumper {
    private final StringBuilder stringBuilder;

    public StringStreamDumper(StringBuilder sb, TypeUsageInformation typeUsageInformation, Options options, IllegalIdentifierDump illegalIdentifierDump) {
        super(typeUsageInformation, options, illegalIdentifierDump);
        this.stringBuilder = sb;
    }

    @Override
    protected void write(String s) {
        stringBuilder.append(s);
    }

    @Override
    public void close() {
    }

    @Override
    public void addSummaryError(Method method, String s) {
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TypeOverridingDumper(this, innerclassTypeUsageInformation);
    }
}