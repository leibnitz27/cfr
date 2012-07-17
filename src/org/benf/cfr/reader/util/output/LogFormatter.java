package org.benf.cfr.reader.util.output;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 17/07/2012
 * Time: 06:32
 */
public class LogFormatter extends Formatter {
    @Override
    public String format(LogRecord logRecord) {
        return logRecord.getMessage() + "\n";
    }
}
