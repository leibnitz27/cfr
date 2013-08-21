package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
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
