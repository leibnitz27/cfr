package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/06/2013
 * Time: 17:54
 */
public interface LValueUsageCollector {
    void collect(LValue lValue);
}
