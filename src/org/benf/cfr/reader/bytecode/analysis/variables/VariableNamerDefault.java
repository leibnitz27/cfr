package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class VariableNamerDefault implements VariableNamer {

    private Map<Long, NamedVariable> cached = MapFactory.newMap();

    public VariableNamerDefault() {
    }

    @Override
    public NamedVariable getName(int originalRawOffset, long stackPosition) {
        NamedVariable res = cached.get(stackPosition);
        if (res == null) {
            res = new NamedVariableDefault("var" + stackPosition);
            cached.put(stackPosition, res);
        }
        return res;
    }

    @Override
    public void forceName(long stackPosition, String name) {
        NamedVariable res = cached.get(stackPosition);
        if (res == null) {
            cached.put(stackPosition, new NamedVariableDefault(name));
            return;
        }
        res.forceName(name);
    }
}
