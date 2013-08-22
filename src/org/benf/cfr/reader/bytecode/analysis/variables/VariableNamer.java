package org.benf.cfr.reader.bytecode.analysis.variables;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public interface VariableNamer {
    NamedVariable getName(int originalRawOffset, long stackPosition);

    void forceName(long stackPosition, String name);
}
