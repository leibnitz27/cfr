package org.benf.cfr.reader.bytecode.analysis.variables;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public interface VariableNamer {
    NamedVariable getName(int originalRawOffset, Ident ident, long stackPosition);

    void forceName(Ident ident, long stackPosition, String name);
}
