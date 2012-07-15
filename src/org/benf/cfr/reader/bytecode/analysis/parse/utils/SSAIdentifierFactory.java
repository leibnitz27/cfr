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
public class SSAIdentifierFactory {
    private static final Map<LValue, Integer> nextIdentFor = MapFactory.newLazyMap(new UnaryFunction<LValue, Integer>() {
        @Override
        public Integer invoke(LValue ignore) {
            return 0;
        }
    });

    public SSAIdent getIdent(LValue lValue) {
        int val = nextIdentFor.get(lValue);
        nextIdentFor.put(lValue, val + 1);
        return new SSAIdent(val);
    }
}
