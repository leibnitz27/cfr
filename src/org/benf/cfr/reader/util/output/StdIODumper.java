package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.state.TypeUsageInformation;

import java.io.PrintStream;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/11/2013
 * Time: 07:29
 */
public class StdIODumper extends StreamDumper {
    public StdIODumper(TypeUsageInformation typeUsageInformation) {
        super(typeUsageInformation);
    }

    @Override
    protected void write(String s) {
        System.out.print(s);
    }

    @Override
    public void close() {
    }
}
