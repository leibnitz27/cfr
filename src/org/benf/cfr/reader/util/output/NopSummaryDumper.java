package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;

public class NopSummaryDumper implements SummaryDumper {

    @Override
    public void notify(String message) {
    }

    @Override
    public void notifyError(JavaTypeInstance controllingType, Method method, String error) {
    }

    @Override
    public void close() {
    }

    @Override
    public void NotifyAdditionalAtEnd() {
    }
}
