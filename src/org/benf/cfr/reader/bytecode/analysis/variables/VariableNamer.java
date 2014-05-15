package org.benf.cfr.reader.bytecode.analysis.variables;

import java.util.List;

public interface VariableNamer {
    NamedVariable getName(int originalRawOffset, Ident ident, long stackPosition);

    List<NamedVariable> getNamedVariables();

    void mutatingRenameUnClash(NamedVariable toRename);

    void forceName(Ident ident, long stackPosition, String name);
}
