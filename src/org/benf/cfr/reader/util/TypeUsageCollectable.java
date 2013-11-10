package org.benf.cfr.reader.util;

import org.benf.cfr.reader.state.TypeUsageCollector;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/11/2013
 * Time: 17:28
 */
public interface TypeUsageCollectable {
    public void collectTypeUsages(TypeUsageCollector collector);
}
