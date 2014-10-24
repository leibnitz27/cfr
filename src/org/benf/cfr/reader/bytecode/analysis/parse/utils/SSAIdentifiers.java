package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.BinaryPredicate;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Map;
import java.util.Set;

public class SSAIdentifiers<KEYTYPE> {

    private final Map<KEYTYPE, SSAIdent> knownIdentifiersOnEntry;
    private final Map<KEYTYPE, SSAIdent> knownIdentifiersOnExit;
    private final Map<KEYTYPE, KEYTYPE> fixedHere;

    public SSAIdentifiers() {
        knownIdentifiersOnEntry = MapFactory.newMap();
        knownIdentifiersOnExit = MapFactory.newMap();
        fixedHere = MapFactory.newMap();
    }

    public SSAIdentifiers(SSAIdentifiers<KEYTYPE> other) {
        this.fixedHere = MapFactory.newMap();
        fixedHere.putAll(other.fixedHere);
        this.knownIdentifiersOnEntry = MapFactory.newMap();
        knownIdentifiersOnEntry.putAll(other.knownIdentifiersOnEntry);
        this.knownIdentifiersOnExit = MapFactory.newMap();
        knownIdentifiersOnExit.putAll(other.knownIdentifiersOnExit);
    }

    public SSAIdentifiers(KEYTYPE lValue, SSAIdentifierFactory<KEYTYPE> ssaIdentifierFactory) {
        SSAIdent id = ssaIdentifierFactory.getIdent(lValue);
        knownIdentifiersOnEntry = MapFactory.newMap();
        knownIdentifiersOnExit = MapFactory.newMap();
        knownIdentifiersOnExit.put(lValue, id);
        fixedHere = MapFactory.newMap();
        fixedHere.put(lValue, lValue);
    }

    public SSAIdentifiers(Map<KEYTYPE, SSAIdent> precomputedIdentifiers) {
        this.knownIdentifiersOnEntry = MapFactory.newMap();
        this.knownIdentifiersOnExit = MapFactory.newMap();
        this.fixedHere = MapFactory.newMap();
        knownIdentifiersOnEntry.putAll(precomputedIdentifiers);
        knownIdentifiersOnExit.putAll(precomputedIdentifiers);
    }

    public boolean mergeWith(SSAIdentifiers<KEYTYPE> other) {
        return mergeWith(other, null);
    }

    private boolean registerChange(Map<KEYTYPE, SSAIdent> knownIdentifiers, KEYTYPE lValue, SSAIdent otherIdent) {
        if (!knownIdentifiers.containsKey(lValue)) {
            knownIdentifiers.put(lValue, otherIdent);
            return true;
        } else {
            // Merge
            SSAIdent oldIdent = knownIdentifiers.get(lValue);
            Object k1 = oldIdent.getComparisonType();
            Object k2 = otherIdent.getComparisonType();
            SSAIdent newIdent;
            if (k1 == k2) {
                newIdent = oldIdent.mergeWith(otherIdent);
            } else {
                newIdent = SSAIdent.poison;
            }

            if (!newIdent.equals(oldIdent)) {
                knownIdentifiers.put(lValue, newIdent);
                return true;
            }
        }
        return false;
    }
    /*
     * We're being called with the idents of our sources.
     */
    public boolean mergeWith(SSAIdentifiers<KEYTYPE> other, BinaryPredicate<KEYTYPE, KEYTYPE> pred) {
        boolean changed = false;
        for (Map.Entry<KEYTYPE, SSAIdent> valueSetEntry : other.knownIdentifiersOnExit.entrySet()) {
            KEYTYPE lValue = valueSetEntry.getKey();
            SSAIdent otherIdent = valueSetEntry.getValue();
            boolean c1 = registerChange(knownIdentifiersOnEntry, lValue, otherIdent);
            boolean skip = false;
            if (fixedHere.containsKey(lValue)) {
                if (pred == null || !pred.test(lValue, fixedHere.get(lValue))) {
                    skip = true;
                }
            }
            boolean c2 = !skip && registerChange(knownIdentifiersOnExit, lValue, otherIdent);
            if (c1 || c2) changed = true;
        }
        return changed;
    }

    public Set<KEYTYPE> getFixedHere() {
        return fixedHere.keySet();
    }

//    public SSAIdent getValFixedHere() {
//        return valFixedHere;
//    }

    /*
     * For an identifier to be a valid replacement, its' SSA identifiers need to match those of the
     * target at point of entry.
     *
     * i.e. v10 = a        (at exit a=1)
     *      v11 = a++      (at exit a=2)
     *      foo(v10, v11), (at entry a=2)
     *
     *
     */
    public boolean isValidReplacement(LValue lValue, SSAIdentifiers<KEYTYPE> other) {
        SSAIdent thisVersion = knownIdentifiersOnEntry.get(lValue);
        SSAIdent otherVersion = other.knownIdentifiersOnExit.get(lValue);
        if (thisVersion == null && otherVersion == null) return true;
        if (thisVersion == null || otherVersion == null) return false;
        boolean res = thisVersion.equals(otherVersion);
        if (res) return true;
        /*
         * Last chance, is otherVersion a subset of thisVersion.
         */
        if (thisVersion.isSuperSet(otherVersion)) return true;
        return false;
    }

    public boolean isValidReplacementOnExit(LValue lValue, SSAIdentifiers<KEYTYPE> other) {
        SSAIdent thisVersion = knownIdentifiersOnExit.get(lValue);
        SSAIdent otherVersion = other.knownIdentifiersOnExit.get(lValue);
        if (thisVersion == null && otherVersion == null) return true;
        if (thisVersion == null || otherVersion == null) return false;
        boolean res = thisVersion.equals(otherVersion);
        if (res) return true;
        /*
         * Last chance, is otherVersion a subset of thisVersion.
         */
        if (thisVersion.isSuperSet(otherVersion)) return true;
        return false;
    }

    public Set<KEYTYPE> getChanges() {
        Set<KEYTYPE> result = SetFactory.newSet();
        for (Map.Entry<KEYTYPE, SSAIdent> entry : knownIdentifiersOnEntry.entrySet()) {
            SSAIdent after = knownIdentifiersOnExit.get(entry.getKey());
            if (after != null && !after.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public SSAIdent getSSAIdentOnExit(KEYTYPE lValue) {
        return knownIdentifiersOnExit.get(lValue);
    }

    public SSAIdent getSSAIdentOnEntry(KEYTYPE lValue) {
        return knownIdentifiersOnEntry.get(lValue);
    }

    public void removeEntryIdent(KEYTYPE key) {
        knownIdentifiersOnEntry.remove(key);
    }

    public void setKnownIdentifierOnExit(KEYTYPE lValue, SSAIdent ident) {
        knownIdentifiersOnExit.put(lValue, ident);
    }

    public void setKnownIdentifierOnEntry(KEYTYPE lValue, SSAIdent ident) {
        knownIdentifiersOnEntry.put(lValue, ident);
    }

    public int sizeOnExit() {
        return knownIdentifiersOnExit.size();
    }

    public Map<KEYTYPE, SSAIdent> getKnownIdentifiersOnExit() {
        return knownIdentifiersOnExit;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<KEYTYPE, SSAIdent> entry : knownIdentifiersOnEntry.entrySet()) {
            sb.append(entry.getKey()).append("@").append(entry.getValue()).append(" ");
        }
        sb.append(" -> ");
        for (Map.Entry<KEYTYPE, SSAIdent> entry : knownIdentifiersOnExit.entrySet()) {
            sb.append(entry.getKey()).append("@").append(entry.getValue()).append(" ");
        }
        return sb.toString();
    }
}

