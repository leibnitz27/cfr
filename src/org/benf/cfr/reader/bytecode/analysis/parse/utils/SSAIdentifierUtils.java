package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

import java.util.Collection;

public class SSAIdentifierUtils {
    public static boolean isMovableUnder(Collection<LValue> lValues, LValue lValueMove, SSAIdentifiers atTarget, SSAIdentifiers atSource) {
        for (LValue lValue : lValues) {
            if (!atTarget.isValidReplacement(lValue, atSource)) return false;
        }
        SSAIdent afterSrc = atSource.getSSAIdentOnExit(lValueMove);
        if (afterSrc == null) return false;
        SSAIdent beforeTarget = atTarget.getSSAIdentOnEntry(lValueMove);
        if (beforeTarget == null) return false;
        if (!beforeTarget.isSuperSet(afterSrc)) return false;
        return true;
    }
}
