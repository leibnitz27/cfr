package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;

public interface SummaryDumper {
    void notify(String message);

    void notifyError(JavaTypeInstance controllingType, Method method, String error);

    void close();

    void NotifyAdditionalAtEnd();
}
