package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableNamerDefault implements VariableNamer {

    private Map<Ident, NamedVariable> cached = MapFactory.newMap();

    public VariableNamerDefault() {
    }

    @Override
    public NamedVariable getName(int originalRawOffset, Ident ident, long stackPosition, boolean clashed) {
        NamedVariable res = cached.get(ident);
        if (res == null) {
            res = new NamedVariableDefault("var" + ident);
            cached.put(ident, res);
        }
        return res;
    }

    @Override
    public void forceName(Ident ident, long stackPosition, String name) {
        NamedVariable res = cached.get(ident);
        if (res == null) {
            cached.put(ident, new NamedVariableDefault(name));
            return;
        }
        res.forceName(name);
    }


    @Override
    public List<NamedVariable> getNamedVariables() {
        return ListFactory.newList(cached.values());
    }

    private final Pattern indexedVarPattern = Pattern.compile("^(.*[^\\d]+)([\\d]+)$");

    @Override
    public void mutatingRenameUnClash(NamedVariable toRename) {
        Collection<NamedVariable> namedVars = cached.values();
        Map<String, NamedVariable> namedVariableMap = MapFactory.newMap();
        for (NamedVariable var : namedVars) {
            namedVariableMap.put(var.getStringName(), var);
        }

        String name = toRename.getStringName();
        Matcher m = indexedVarPattern.matcher(name);
        int start = 2;
        String prefix = name;
        if (m.matches()) {
            prefix = m.group(0);
            start = Integer.parseInt(m.group(1));
            start++;
        }
        do {
            String name2 = prefix + start;
            if (!namedVariableMap.containsKey(name2)) {
                toRename.forceName(name2);
                return;
            }
            start++;
        } while (true);
    }
}
