package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;

/**
 * Created:
 * User: lee
 * Date: 11/04/2012
 */
public interface ConstantPoolEntryLiteral {
    StackType getStackType();
}
