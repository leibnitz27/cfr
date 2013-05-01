package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.KnowsRawName;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 22:01
 * To change this template use File | Settings | File Templates.
 */
public abstract class Attribute implements KnowsRawSize, KnowsRawName {

    /*
     * NB : we need the constant pool, as eg annotations have entries.
     */
    public abstract void dump(Dumper d);


}
