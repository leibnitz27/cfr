package org.benf.cfr.reader.bytecode.analysis.opgraph.op02obf;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;

import java.util.List;
import java.util.SortedMap;

public class Op02Obf {
    public static void removeControlFlowExceptions(Method method, ExceptionAggregator exceptions, List<Op02WithProcessedDataAndRefs> op2list, SortedMap<Integer, Integer> lutByOffset) {
        new ControlFlowExceptions().process(method, exceptions, op2list, lutByOffset);
    }
}
