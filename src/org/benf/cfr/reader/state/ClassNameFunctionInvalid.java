package org.benf.cfr.reader.state;

import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ClassNameFunctionInvalid implements ClassNameFunction {
    private final Set<String> illegalNames;

    ClassNameFunctionInvalid(boolean caseInsensitive, Set<String> illegalNames) {
        if (caseInsensitive) {
            Set<String> ciNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            ciNames.addAll(illegalNames);
            illegalNames = ciNames;
        }
        this.illegalNames = illegalNames;
    }

    @Override
    public Map<String, String> apply(Map<String, String> names) {
        // Any class names which would create files in the illegal set need to be renamed.
        Map<String, String> res = MapFactory.newOrderedMap();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String val = entry.getValue();
            if (illegalName(val)) {
                val = val.substring(0, val.length()-6) + "_.class";
            }
            res.put(entry.getKey(), val);
        }
        return res;
    }

    private boolean illegalName(String path) {
        String stripClass = path.substring(0, path.length() - 6);
        int idx = stripClass.lastIndexOf("/");
        if (idx != -1) {
            stripClass = stripClass.substring(idx+1);
        }
        return illegalNames.contains(stripClass);
    }
}
