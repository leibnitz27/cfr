package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class VariableNamerDefault implements VariableNamer {

    private Map<Ident, NamedVariable> cached = MapFactory.newMap();

    public VariableNamerDefault() {
    }

    @Override
    public NamedVariable getName(int originalRawOffset, Ident ident, long stackPosition) {
        NamedVariable res = cached.get(ident);
        if (res == null) {
            res = new NamedVariableDefault("var" + ident);
            cached.put(ident, res);
        }
        return res;
    }

    @Override
    public void forceName(Ident ident, long stackPosition, String name) {
        NamedVariable res = cached.get(ident);
        if (res == null) {
            cached.put(ident, new NamedVariableDefault(name));
            return;
        }
        res.forceName(name);
    }
}
