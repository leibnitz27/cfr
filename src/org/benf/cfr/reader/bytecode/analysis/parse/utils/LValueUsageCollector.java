package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

public interface LValueUsageCollector {
    void collect(LValue lValue);
}
