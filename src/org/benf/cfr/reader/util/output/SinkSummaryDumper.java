package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;

public class SinkSummaryDumper implements SummaryDumper {
    private final OutputSinkFactory.Sink sink;

    private transient JavaTypeInstance lastControllingType = null;
    private transient Method lastMethod = null;

    SinkSummaryDumper(OutputSinkFactory.Sink sink) {
        this.sink = sink;
    }

    @Override
    public void notify(String message) {
        sink.write(message + "\n");
    }

    @Override
    public void notifyError(JavaTypeInstance controllingType, Method method, String error) {
        if (lastControllingType != controllingType) {
            lastControllingType = controllingType;
            lastMethod = null;
            sink.write("\n\n" + controllingType.getRawName() + "\n----------------------------\n\n");
        }
        if (method != lastMethod) {
            sink.write(method.getMethodPrototype().toString() + "\n");
            lastMethod = method;
        }
        sink.write("  " + error + "\n");
    }

    @Override
    public void close() {
    }
}
