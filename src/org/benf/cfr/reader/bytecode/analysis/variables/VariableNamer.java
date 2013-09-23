package org.benf.cfr.reader.bytecode.analysis.variables;

import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public interface VariableNamer {
    NamedVariable getName(int originalRawOffset, Ident ident, long stackPosition);

    List<NamedVariable> getNamedVariables();

    void mutatingRenameUnClash(NamedVariable toRename);

    void forceName(Ident ident, long stackPosition, String name);
}
