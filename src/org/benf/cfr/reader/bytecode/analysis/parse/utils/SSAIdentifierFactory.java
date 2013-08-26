package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

/**
 * Created:
 * User: lee
 * Date: 24/04/2012
 */
public class SSAIdentifierFactory<KEYTYPE> {
    private final Map<KEYTYPE, Integer> nextIdentFor = MapFactory.newLazyMap(
            MapFactory.<KEYTYPE, Integer>newOrderedMap(),
            new UnaryFunction<KEYTYPE, Integer>() {
                @Override
                public Integer invoke(KEYTYPE ignore) {
                    return 0;
                }
            });

    public SSAIdent getIdent(KEYTYPE lValue) {
        int val = nextIdentFor.get(lValue);
        nextIdentFor.put(lValue, val + 1);
        return new SSAIdent(val);
    }
}
