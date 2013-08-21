package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.attributes.LocalVariableEntry;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2012
 */
public class VariableNamerHinted implements VariableNamer {
    private final OrderLocalVariables orderLocalVariable = new OrderLocalVariables();
    private final Map<Short, TreeSet<LocalVariableEntry>> localVariableEntryTreeSet =
            MapFactory.newLazyMap(new UnaryFunction<Short, TreeSet<LocalVariableEntry>>() {
                @Override
                public TreeSet<LocalVariableEntry> invoke(Short arg) {
                    return new TreeSet<LocalVariableEntry>(orderLocalVariable);
                }
            });

    private final ConstantPool cp;


    public VariableNamerHinted(List<LocalVariableEntry> entryList, ConstantPool cp) {
        for (LocalVariableEntry e : entryList) {
            localVariableEntryTreeSet.get(e.getIndex()).add(e);
        }
        this.cp = cp;
    }

    @Override
    public String getName(int originalRawOffset, long stackPosition) {
        originalRawOffset += 2;

        short sstackPos = (short) stackPosition;
        if (!localVariableEntryTreeSet.containsKey(sstackPos)) {
            return "var" + stackPosition;
        }
        LocalVariableEntry tmp = new LocalVariableEntry((short) (originalRawOffset), (short) 1, (short) -1, (short) -1, (short) stackPosition);
        LocalVariableEntry lve = localVariableEntryTreeSet.get(sstackPos).floor(tmp);

        if (lve == null) {
            return "var" + stackPosition;
        }

        if (lve.getIndex() == stackPosition &&
                lve.getStartPc() <= (originalRawOffset) &&
                (lve.getStartPc() + lve.getLength()) >= originalRawOffset) {
            return cp.getUTF8Entry(lve.getNameIndex()).getValue();
        } else {
            return cp.getUTF8Entry(lve.getNameIndex()).getValue();
//
//            String lveName = cp.getUTF8Entry(lve.getNameIndex()).getValue();
//            return "unnamed_local_" + lveName + "_" + stackPosition;
        }

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
    public void forceName(long stackPosition, String name) {

    }
}
