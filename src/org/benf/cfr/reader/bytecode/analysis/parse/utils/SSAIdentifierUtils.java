package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 20/06/2012
 * Time: 06:09
 */
public class SSAIdentifierUtils {
    public static boolean isMovableUnder(Collection<LValue> lValues, SSAIdentifiers atTarget, SSAIdentifiers atSource) {
        for (LValue lValue : lValues) {
            if (!atTarget.isValidReplacement(lValue, atSource)) return false;
        }
        return true;
    }
}
