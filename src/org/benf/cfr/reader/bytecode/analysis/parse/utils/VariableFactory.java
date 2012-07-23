package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 20/07/2012
 * Time: 18:15
 */
public class VariableFactory {
    private final VariableNamer variableNamer;

    private final Map<LValue, LValue> cache = MapFactory.newMap();

    public VariableFactory(VariableNamer variableNamer) {
        this.variableNamer = variableNamer;
    }

    public LValue localVariable(int idx, int origRawOffset) {
        LValue tmp = new LocalVariable(idx, variableNamer, origRawOffset);
        LValue val = cache.get(tmp);
        if (val == null) {
            cache.put(tmp, tmp);
            val = tmp;
        }
        return val;
    }
}
