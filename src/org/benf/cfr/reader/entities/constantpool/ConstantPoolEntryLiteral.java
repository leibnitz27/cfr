package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;

public interface ConstantPoolEntryLiteral {
    StackType getStackType();
}
