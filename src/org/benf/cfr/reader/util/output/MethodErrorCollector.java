package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;

public interface MethodErrorCollector {
    void addSummaryError(Method method, String s);

    static class SummaryDumperMethodErrorCollector implements MethodErrorCollector {
        private final JavaTypeInstance type;
        private final SummaryDumper summaryDumper;

        public SummaryDumperMethodErrorCollector(JavaTypeInstance type, SummaryDumper summaryDumper) {
            this.type = type;
            this.summaryDumper = summaryDumper;
        }

        @Override
        public void addSummaryError(Method method, String s) {
            summaryDumper.notifyError(type, method, s);
        }
    }
}
