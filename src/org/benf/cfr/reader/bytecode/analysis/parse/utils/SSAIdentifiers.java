package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created:
 * User: lee
 * Date: 24/04/2012
 */
public class SSAIdentifiers {

    private final LValue fixedHere;
    private final Map<LValue, SSAIdent> knownIdentifiers = MapFactory.newMap();

    public SSAIdentifiers() {
        fixedHere = null;
    }

    public SSAIdentifiers(LValue lValue, SSAIdentifierFactory ssaIdentifierFactory) {
        SSAIdent id = ssaIdentifierFactory.getIdent(lValue);
        fixedHere = lValue;
        knownIdentifiers.put(lValue, id);
    }

    public boolean mergeWith(SSAIdentifiers other) {
        boolean changed = false;
        for (Map.Entry<LValue, SSAIdent> valueSetEntry : other.knownIdentifiers.entrySet()) {
            LValue lValue = valueSetEntry.getKey();
            SSAIdent otherIdent = valueSetEntry.getValue();
            if (lValue.equals(fixedHere)) continue;
            if (!knownIdentifiers.containsKey(lValue)) {
                knownIdentifiers.put(lValue, otherIdent);
            } else {
                // Merge
                SSAIdent oldIdent = knownIdentifiers.get(lValue);
                SSAIdent newIdent = oldIdent.mergeWith(otherIdent);
                if (!newIdent.equals(oldIdent)) {
                    knownIdentifiers.put(lValue, newIdent);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public boolean isValidReplacement(LValue lValue, SSAIdentifiers other) {
        SSAIdent thisVersion = knownIdentifiers.get(lValue);
        SSAIdent otherVersion = other.knownIdentifiers.get(lValue);
        if (thisVersion == null && otherVersion == null) return true;
        if (thisVersion == null || otherVersion == null) return false;
        return thisVersion.equals(otherVersion);
    }

    public int size() {
        return knownIdentifiers.size();
    }
}
