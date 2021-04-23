package org.benf.cfr.reader.bytecode.analysis.loc;

import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/*
 * The amount of checks for DISABLED in here is very annoying.  But we want the convenience of not having to pass a
 * factory around at the point where we combine locations, along with being able to hardcode usages of NONE/(t)ODO.
 */
public class BytecodeLocFactoryImpl implements BytecodeLocFactory {
    public static BytecodeLocFactoryImpl INSTANCE = new BytecodeLocFactoryImpl();

    private BytecodeLocFactoryImpl() {
    }

    @Override
    public BytecodeLoc at(int originalRawOffset, Method method) {
        if (originalRawOffset < 0) return BytecodeLoc.NONE;
        return new BytecodeLocSimple(originalRawOffset, method);
    }

    public BytecodeLoc combine(HasByteCodeLoc primary, HasByteCodeLoc... coll) {
        BytecodeLoc primaryLoc = primary.getLoc();
        if (primaryLoc == BytecodeLocFactory.DISABLED) return BytecodeLocFactory.DISABLED;
        BytecodeLocCollector bcl = new BytecodeLocCollector();
        primaryLoc.addTo(bcl);
        BytecodeLoc loc1 = getLocs(coll, bcl);
        if (loc1 != null) return loc1;
        return bcl.getLoc();
    }

    public BytecodeLoc combine(HasByteCodeLoc primary, Collection<? extends HasByteCodeLoc> coll1, HasByteCodeLoc... coll2) {
        BytecodeLoc primaryLoc = primary.getLoc();
        if (primaryLoc == BytecodeLocFactory.DISABLED) return BytecodeLocFactory.DISABLED;
        BytecodeLocCollector bcl = new BytecodeLocCollector();
        primaryLoc.addTo(bcl);
        BytecodeLoc loc1 = getLocs(coll1, bcl);
        if (loc1 != null) return loc1;
        BytecodeLoc loc = getLocs(coll2, bcl);
        if (loc != null) return loc;
        return bcl.getLoc();
    }

    public BytecodeLoc combineShallow(HasByteCodeLoc... coll) {
        if (coll.length == 0 || coll[0].getLoc() == BytecodeLocFactory.DISABLED) return BytecodeLocFactory.DISABLED;
        BytecodeLocCollector bcl = new BytecodeLocCollector();
        BytecodeLoc loc1 = getLocs(coll, bcl);
        if (loc1 != null) return loc1;
        return bcl.getLoc();
    }

    private static BytecodeLoc getLocs(HasByteCodeLoc[] sources, BytecodeLocCollector bcl) {
        for (HasByteCodeLoc source : sources) {
            if (source == null) continue;
            BytecodeLoc loc = source.getCombinedLoc();
            if (loc == BytecodeLocFactory.DISABLED) return loc;
            loc.addTo(bcl);
        }
        return null;
    }

    private static BytecodeLoc getLocs(Collection<? extends HasByteCodeLoc> sources, BytecodeLocCollector bcl) {
        for (HasByteCodeLoc source : sources) {
            if (source == null) continue;
            BytecodeLoc loc = source.getCombinedLoc();
            if (loc == BytecodeLocFactory.DISABLED) return loc;
            loc.addTo(bcl);
        }
        return null;
    }
}
