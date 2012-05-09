package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Map;
import java.util.Set;

/**
 * Created:
 * User: lee
 * Date: 24/04/2012
 */
public class SSAIdentifiers {
    private final static Integer AMBIGUOUS_IDENT = -1;

    private final LValue fixedHere;
    private final Map<LValue, Integer> knownIdentifiers = MapFactory.newMap();

    public SSAIdentifiers() {
        fixedHere = null;
    }

    public SSAIdentifiers(LValue lValue, SSAIdentifierFactory ssaIdentifierFactory) {
        Integer id = ssaIdentifierFactory.getIdent(lValue);
        fixedHere = lValue;
        knownIdentifiers.put(lValue, id);
    }

    public boolean mergeWith(SSAIdentifiers other) {
        boolean changed = false;
        for (Map.Entry<LValue, Integer> valueSetEntry : other.knownIdentifiers.entrySet()) {
            LValue lValue = valueSetEntry.getKey();
            Integer otherIdent = valueSetEntry.getValue();
            if (lValue.equals(fixedHere)) continue;
            if (!knownIdentifiers.containsKey(lValue)) knownIdentifiers.put(lValue, otherIdent);
            if (!knownIdentifiers.get(lValue).equals(otherIdent)) {
                knownIdentifiers.put(lValue, AMBIGUOUS_IDENT);
                changed = true;
            }
        }
        return changed;
    }

    public boolean isValidReplacement(LValue lValue, SSAIdentifiers other) {
        Integer thisVersion = knownIdentifiers.get(lValue);
        Integer otherVersion = other.knownIdentifiers.get(lValue);
        if (thisVersion == null && otherVersion == null) return true;
        if (thisVersion == null || thisVersion == AMBIGUOUS_IDENT) return false;
        if (otherVersion == null || otherVersion == AMBIGUOUS_IDENT) return false;
        return thisVersion.equals(otherVersion);
    }

    public int size() {
        return knownIdentifiers.size();
    }
}
