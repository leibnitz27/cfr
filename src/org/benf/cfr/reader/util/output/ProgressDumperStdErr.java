package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

public class ProgressDumperStdErr implements ProgressDumper {

    @Override
    public void analysingType(JavaTypeInstance type) {
        System.err.println("Processing " + type.getRawName());
    }

    @Override
    public void analysingPath(String path) {
        System.err.println("Processing " + path + " (use " + OptionsImpl.SILENT.getName() + " to silence)");
    }
}
