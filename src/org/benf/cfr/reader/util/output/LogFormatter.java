package org.benf.cfr.reader.util.output;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
    @Override
    public String format(LogRecord logRecord) {
        return logRecord.getMessage() + "\n";
    }
}
