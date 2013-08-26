package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.BinaryPredicate;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created:
 * User: lee
 * Date: 24/04/2012
 */
public class SSAIdentifiers<KEYTYPE> {

    private final KEYTYPE fixedHere;
    private final Map<KEYTYPE, SSAIdent> knownIdentifiers;

    public SSAIdentifiers() {
        fixedHere = null;
        knownIdentifiers = MapFactory.newMap();
    }

    public SSAIdentifiers(KEYTYPE lValue, SSAIdentifierFactory<KEYTYPE> ssaIdentifierFactory) {
        SSAIdent id = ssaIdentifierFactory.getIdent(lValue);
        fixedHere = lValue;
        knownIdentifiers = MapFactory.newMap();
        knownIdentifiers.put(lValue, id);
    }

    public SSAIdentifiers(Map<KEYTYPE, SSAIdent> precomputedIdentifiers) {
        this.knownIdentifiers = precomputedIdentifiers;
        this.fixedHere = null;
    }

    public boolean mergeWith(SSAIdentifiers<KEYTYPE> other) {
        return mergeWith(other, null);
    }

    public boolean mergeWith(SSAIdentifiers<KEYTYPE> other, BinaryPredicate<KEYTYPE, KEYTYPE> pred) {
        boolean changed = false;
        for (Map.Entry<KEYTYPE, SSAIdent> valueSetEntry : other.knownIdentifiers.entrySet()) {
            KEYTYPE lValue = valueSetEntry.getKey();
            SSAIdent otherIdent = valueSetEntry.getValue();
            if (lValue.equals(fixedHere)) {
                if (pred == null || !pred.test(lValue, fixedHere)) {
                    continue;
                }
            }
            if (!knownIdentifiers.containsKey(lValue)) {
                knownIdentifiers.put(lValue, otherIdent);
                changed = true;
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

    public boolean isFixedHere(KEYTYPE lValue) {
        return lValue.equals(fixedHere);
    }

    public boolean isValidReplacement(LValue lValue, SSAIdentifiers<KEYTYPE> other) {
        SSAIdent thisVersion = knownIdentifiers.get(lValue);
        SSAIdent otherVersion = other.knownIdentifiers.get(lValue);
        if (thisVersion == null && otherVersion == null) return true;
        if (thisVersion == null || otherVersion == null) return false;
        return thisVersion.equals(otherVersion);
    }

    public SSAIdent getSSAIdent(KEYTYPE lValue) {
        return knownIdentifiers.get(lValue);
    }

    public int size() {
        return knownIdentifiers.size();
    }

    public Map<KEYTYPE, SSAIdent> getKnownIdentifiers() {
        return knownIdentifiers;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<KEYTYPE, SSAIdent> entry : knownIdentifiers.entrySet()) {
            sb.append("" + entry.getKey() + "@" + entry.getValue() + " ");
        }
        return sb.toString();
    }
}

