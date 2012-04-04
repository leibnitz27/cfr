package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.entities.ConstantPool;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 04/05/2011
 * Time: 18:04
 * To change this template use File | Settings | File Templates.
 */
public interface IndexedOperation {
    public int[] getTargetIndexOffsets();
    public int getStackDelta(ConstantPool cp);
}