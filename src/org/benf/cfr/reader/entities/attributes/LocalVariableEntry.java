package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class LocalVariableEntry {
    private final short startPc;
    private final short length;
    private final short nameIndex;
    private final short descriptorIndex;
    private final short index;

    public LocalVariableEntry(short startPc, short length, short nameIndex, short descriptorIndex, short index) {
        this.startPc = startPc;
        this.length = length;
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
        this.index = index;
    }

    public void dump(Dumper d, ConstantPool cp) {
        d.print("Variable # " + index + " @" + startPc + "[" + length + "] : " + cp.getUTF8Entry(nameIndex).getValue() + "\n");
    }

    public short getStartPc() {
        return startPc;
    }

    public short getLength() {
        return length;
    }

    public short getNameIndex() {
        return nameIndex;
    }

    public short getDescriptorIndex() {
        return descriptorIndex;
    }

    public short getIndex() {
        return index;
    }
}
