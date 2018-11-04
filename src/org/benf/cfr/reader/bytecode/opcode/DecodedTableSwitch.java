package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DecodedTableSwitch implements DecodedSwitch {
    private static final int OFFSET_OF_DEFAULT = 0;
    private static final int OFFSET_OF_LOWBYTE = 4;
    private static final int OFFSET_OF_HIGHBYTE = 8;
    private static final int OFFSET_OF_OFFSETS = 12;

    private final List<DecodedSwitchEntry> jumpTargets;

    /*
     * Note that offsetOfOriginalInstruction is data[-1]
     */
    public DecodedTableSwitch(byte[] data, int offsetOfOriginalInstruction) {
        int curoffset = offsetOfOriginalInstruction + 1;
        int overflow = (curoffset % 4);
        int offset = overflow > 0 ? 4 - overflow : 0;

        ByteData bd = new BaseByteData(data);
        int defaultvalue = bd.getS4At(offset + OFFSET_OF_DEFAULT);
        int lowvalue = bd.getS4At(offset + OFFSET_OF_LOWBYTE);
        int highvalue = bd.getS4At(offset + OFFSET_OF_HIGHBYTE);
        int numoffsets = highvalue - lowvalue + 1;
        int defaultTarget = defaultvalue;
        int startValue = lowvalue;

        // Treemap so that targets are in bytecode order.
        Map<Integer, List<Integer>> uniqueTargets = MapFactory.newLazyMap(
                new TreeMap<Integer, List<Integer>>(),
                new UnaryFunction<Integer, List<Integer>>() {
                    @Override
                    public List<Integer> invoke(Integer arg) {
                        return ListFactory.newList();
                    }
                });
        uniqueTargets.get(defaultTarget).add(null);
        for (int x = 0; x < numoffsets; ++x) {
            int target = bd.getS4At(offset + OFFSET_OF_OFFSETS + (x * 4));
            if (target != defaultTarget) {
                uniqueTargets.get(target).add(startValue + x);
            }
        }

        jumpTargets = ListFactory.newList();
        for (Map.Entry<Integer, List<Integer>> entry : uniqueTargets.entrySet()) {
            jumpTargets.add(new DecodedSwitchEntry(entry.getValue(), entry.getKey()));
        }

    }

    @Override
    public List<DecodedSwitchEntry> getJumpTargets() {
        return jumpTargets;
    }
}
