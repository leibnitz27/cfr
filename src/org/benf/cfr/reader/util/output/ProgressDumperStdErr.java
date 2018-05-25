package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

public class ProgressDumperStdErr implements ProgressDumper {

    @Override
    public void analysingType(JavaTypeInstance type) {
        System.err.println("Processing " + type.getRawName());
    }
}
