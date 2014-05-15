package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;

public abstract class AbstractConstantPoolEntry implements ConstantPoolEntry {
    private final ConstantPool cp;

    protected AbstractConstantPoolEntry(ConstantPool cp) {
        this.cp = cp;
    }

    protected ConstantPool getCp() {
        if (!cp.isLoaded()) {
            throw new IllegalStateException("Attempt to use constant pool before it's fully loaded.");
        }
        return cp;
    }
}
