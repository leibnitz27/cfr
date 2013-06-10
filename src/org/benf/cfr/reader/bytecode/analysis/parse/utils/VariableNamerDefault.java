package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class VariableNamerDefault implements VariableNamer {
    public VariableNamerDefault() {
    }

    @Override
    public String getName(int originalRawOffset, long stackPosition) {
        return "var" + stackPosition;
    }
}
