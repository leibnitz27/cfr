package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/03/2013
 * Time: 09:26
 */
public interface StackDelta {
    boolean isNoOp();

    StackTypes getConsumed();

    StackTypes getProduced();

    long getChange();
}
