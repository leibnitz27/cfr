package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.variables.Keywords;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.Map;

public class IllegalIdentifierReplacement implements IllegalIdentifierDump {
    private final Map<String, Integer> identifiers = MapFactory.newMap();
    private final Map<String, String> classes = MapFactory.newMap();
    private static final Map<String, Boolean> known = MapFactory.newIdentityMap();
    private int next = 0;

    private static final IllegalIdentifierReplacement instance = new IllegalIdentifierReplacement();

    static {
        known.put(MiscConstants.THIS, true);
        known.put(MiscConstants.NEW, true);
    }

    private IllegalIdentifierReplacement() {
    }

    private String renamedIdent(Integer key) {
        return "cfr_renamed_" + key;
    }

    /*
     * Used externally to test if this should be active.
     *
     * This is probably quite expensive. :(
     *
     * However, if you look at the content of java.lang.CharacterData00, you can see why I'd rather not
     * reimplement this particular wheel.
     */
    private static boolean isIllegalIdentifier(String identifier) {
        if (Keywords.isAKeyword(identifier)) return true;
        if (identifier.length() == 0) return false;
        char[] chars = identifier.toCharArray();
        if (!Character.isJavaIdentifierStart(chars[0])) {
            return true;
        }
        for (int x=1;x<chars.length;++x) {
            if (!Character.isJavaIdentifierPart(chars[x])) return true;
        }
        return false;
    }

    // Ending in .this is a hack, need to fix .this appending elsewhere.
    public static boolean isIllegal(String identifier) {
        if (!isIllegalIdentifier(identifier)) return false;
        if (identifier.endsWith(MiscConstants.DOT_THIS)) return false;
        if (known.containsKey(identifier)) return false;
        return true;
    }


    public static boolean isIllegalMethodName(String name) {
        if (name.equals(MiscConstants.INIT_METHOD)) return false;
        if (name.equals(MiscConstants.STATIC_INIT_METHOD)) return false;
        return isIllegal(name);
    }

    @Override
    public String getLegalIdentifierFor(String identifier) {
        Integer idx = identifiers.get(identifier);
        if (idx != null) {
            if (idx == -1) return identifier;
            return renamedIdent(idx);
        }
        if (isIllegal(identifier)) {
            idx = next++;
            identifiers.put(identifier, idx);
            return renamedIdent(idx);
        } else {
            identifiers.put(identifier, -1);
            return identifier;
        }
    }

    @Override
    public String getLegalShortName(String shortName) {
        String key = classes.get(shortName);
        if (key != null) {
            if (key.isEmpty()) return shortName;
            return key;
        }
        if (isIllegal(shortName)) {
            String testPrefix = "_" + shortName;
            String replace = isIllegal(testPrefix) ? "CfrRenamed" + (classes.size()) : testPrefix;
            classes.put(shortName, replace);
            return replace;
        } else {
            classes.put(shortName, "");
            return shortName;
        }
    }

    public static IllegalIdentifierReplacement getInstance() { return instance; }

}
