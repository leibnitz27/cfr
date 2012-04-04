package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.attributes.AttributeLocalVariableTable;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class VariableNamerFactory {
    public static VariableNamer getNamer(AttributeLocalVariableTable source, ConstantPool cp) {
        if (source == null) return new VariableNamerDefault();
        return new VariableNamerHinted(source.getLocalVariableEntryList(), cp);
    }
}
