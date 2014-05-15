package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.attributes.AttributeLocalVariableTable;

public class VariableNamerFactory {
    public static VariableNamer getNamer(AttributeLocalVariableTable source, ConstantPool cp) {
        if (source == null) return new VariableNamerDefault();
        return new VariableNamerHinted(source.getLocalVariableEntryList(), cp);
    }
}
