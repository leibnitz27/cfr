package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.Map;

public class IllegalIdentifierReplacement implements IllegalIdentifierDump {
    private final Map<String, Integer> rewrites = MapFactory.newMap();
    private int next = 0;

    private static final IllegalIdentifierReplacement instance = new IllegalIdentifierReplacement();


    private IllegalIdentifierReplacement() {
    }

    private String illegal(String key) {
        return "cfr_renamed_" + rewrites.get(key);
    }

    /*
     * Used externally to test if this should be active.
     *
     * This is probably quite expensive. :(
     *
     * However, if you look at the content of java.lang.CharacterData00, you can see why I'd rather not
     * reimplement this particular wheel.
     */
    public static boolean isIllegal(String identifier) {
        if (identifier.length() == 0) return false;
        char[] chars = identifier.toCharArray();
        if (!Character.isJavaIdentifierStart(chars[0])) return true;
        for (int x=1;x<chars.length;++x) {
            if (!Character.isJavaIdentifierPart(chars[x])) return true;
        }
        return false;
    }

    public static boolean isIllegalMethodName(String name) {
        if (name.equals(MiscConstants.INIT_METHOD)) return false;
        if (name.equals(MiscConstants.STATIC_INIT_METHOD)) return false;
        return isIllegal(name);
    }

    @Override
    public String getLegalIdentifierFor(String identifier) {
        if (rewrites.containsKey(identifier)) {
            return illegal(identifier);
        }
        if (isIllegal(identifier)) {
            rewrites.put(identifier, next++);
            return illegal(identifier);
        }
        return identifier;
    }

    public static IllegalIdentifierDump getInstance() { return instance; }

}
