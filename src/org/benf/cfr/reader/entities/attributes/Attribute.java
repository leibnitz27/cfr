package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.KnowsRawName;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 22:01
 * To change this template use File | Settings | File Templates.
 */
public abstract class Attribute implements KnowsRawSize, KnowsRawName, Dumpable, TypeUsageCollectable {

    @Override
    public abstract Dumper dump(Dumper d);

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }
}
