package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.attributes.LocalVariableEntry;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.IllegalIdentifierReplacement;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableNamerHinted implements VariableNamer {

    private int genIdx = 0;
    private final VariableNamer missingNamer = new VariableNamerDefault();

    private final OrderLocalVariables orderLocalVariable = new OrderLocalVariables();
    private final Map<Short, TreeSet<LocalVariableEntry>> localVariableEntryTreeSet =
            MapFactory.newLazyMap(new UnaryFunction<Short, TreeSet<LocalVariableEntry>>() {
                @Override
                public TreeSet<LocalVariableEntry> invoke(Short arg) {
                    return new TreeSet<LocalVariableEntry>(orderLocalVariable);
                }
            });
    //    private final Map<Pair<LocalVariableEntry, Ident>, NamedVariable> cache = MapFactory.newMap();
    private final Map<LocalVariableEntry, NamedVariable> cache = MapFactory.newMap();

    private final ConstantPool cp;


    public VariableNamerHinted(List<LocalVariableEntry> entryList, ConstantPool cp) {
        for (LocalVariableEntry e : entryList) {
            localVariableEntryTreeSet.get(e.getIndex()).add(e);
        }
        this.cp = cp;
    }

    @Override
    public NamedVariable getName(int originalRawOffset, Ident ident, long stackPosition) {
        originalRawOffset += 2;

        short sstackPos = (short) stackPosition;
        if (!localVariableEntryTreeSet.containsKey(sstackPos)) {
            return missingNamer.getName(0, ident, sstackPos);
        }
        LocalVariableEntry tmp = new LocalVariableEntry((short) (originalRawOffset), (short) 1, (short) -1, (short) -1, (short) stackPosition);
        LocalVariableEntry lve = localVariableEntryTreeSet.get(sstackPos).floor(tmp);

        if (lve == null) {
            return missingNamer.getName(0, ident, sstackPos);
        }

//        Pair<LocalVariableEntry, Ident> key = Pair.make(lve, ident);
        LocalVariableEntry key = lve;
        NamedVariable namedVariable = cache.get(key);
        if (namedVariable == null) {
            String name = cp.getUTF8Entry(lve.getNameIndex()).getValue();
            if (IllegalIdentifierReplacement.isIllegal(name)) {
                namedVariable = new NamedVariableDefault(name);
            } else {
                namedVariable = new NamedVariableFromHint(name, lve.getIndex(), genIdx);
            }
            cache.put(key, namedVariable);
        }
        return namedVariable;

//        if (lve.getIndex() == stackPosition &&
//                lve.getStartPc() <= (originalRawOffset) &&
//                (lve.getStartPc() + lve.getLength()) >= originalRawOffset) {
//        } else {
//            return cp.getUTF8Entry(lve.getNameIndex()).getValue();
//        }

    }

    private static class OrderLocalVariables implements Comparator<LocalVariableEntry> {
        @Override
        public int compare(LocalVariableEntry a, LocalVariableEntry b) {
            int x = a.getIndex() - b.getIndex();
            if (x != 0) return x;
            return a.getStartPc() - b.getStartPc();
        }
    }


    @Override
    public List<NamedVariable> getNamedVariables() {
        return ListFactory.newList(cache.values());
    }

    @Override
    public void forceName(Ident ident, long stackPosition, String name) {

    }

    @Override
    public void mutatingRenameUnClash(NamedVariable toRename) {
        Map<String, NamedVariable> namedVariableMap = MapFactory.newMap();
        for (NamedVariable var : cache.values()) {
            namedVariableMap.put(var.getStringName(), var);
        }
        for (NamedVariable var : missingNamer.getNamedVariables()) {
            namedVariableMap.put(var.getStringName(), var);
        }

        String name = toRename.getStringName();
        Pattern p = Pattern.compile("^(.*[^\\d]+)([\\d]+)$");
        Matcher m = p.matcher(name);
        int start = 2;
        String prefix = name;
        if (m.matches()) {
            prefix = m.group(1);
            String numPart = m.group(2);
            start = Integer.parseInt(numPart);
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
