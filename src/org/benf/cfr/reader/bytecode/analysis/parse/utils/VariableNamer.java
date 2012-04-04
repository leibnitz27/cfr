package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.entities.attributes.AttributeLocalVariableTable;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public interface VariableNamer {
    String getName(int originalRawOffset, long stackPosition);
}
