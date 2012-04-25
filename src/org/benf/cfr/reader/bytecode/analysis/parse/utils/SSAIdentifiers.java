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
    private final LValue fixedHere;
    private final Map<LValue, Set<Integer>> knownIdentifiers = MapFactory.newMap();

    public SSAIdentifiers() {
        fixedHere = null;
    }

    public SSAIdentifiers(LValue lValue, SSAIdentifierFactory ssaIdentifierFactory) {
        Set<Integer> values = SetFactory.newSet();
        values.add(ssaIdentifierFactory.getIdent(lValue));
        fixedHere = lValue;
        knownIdentifiers.put(lValue, values);
    }

    public boolean mergeWith(SSAIdentifiers other) {
        boolean changed = false;
        for (Map.Entry<LValue, Set<Integer>> valueSetEntry : other.knownIdentifiers.entrySet()) {
            LValue lValue = valueSetEntry.getKey();
            Set<Integer> set = valueSetEntry.getValue();
            if (lValue.equals(fixedHere)) continue;
            if (!knownIdentifiers.containsKey(lValue)) knownIdentifiers.put(lValue, SetFactory.<Integer>newSet());
            int size = knownIdentifiers.size();
            knownIdentifiers.get(lValue).addAll(set);
            if (knownIdentifiers.size() != size) changed = true;
        }
        return changed;
    }

    public boolean isValidReplacement(LValue lValue, SSAIdentifiers other) {
        Set<Integer> vals = knownIdentifiers.get(lValue);
        Set<Integer> otherVals = other.knownIdentifiers.get(lValue);
        if (vals == null && otherVals == null) return true;
        if (vals == null || vals.size() != 1) return false;
        if (otherVals == null || otherVals.size() != 1) return false;
        return vals.containsAll(otherVals);
    }
}
