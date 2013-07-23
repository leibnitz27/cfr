package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class VariableNamerDefault implements VariableNamer {

    private Map<Long, String> forced = MapFactory.newMap();

    public VariableNamerDefault() {
    }

    @Override
    public String getName(int originalRawOffset, long stackPosition) {
        String forcedName = forced.get(stackPosition);
        if (forcedName != null) return forcedName;
        return "var" + stackPosition;
    }

    @Override
    public void forceName(long stackPosition, String name) {
        forced.put(stackPosition, name);
    }
}
