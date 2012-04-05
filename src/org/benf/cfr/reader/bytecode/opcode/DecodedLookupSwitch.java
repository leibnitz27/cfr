package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/04/2012
 */
public class DecodedLookupSwitch implements DecodedSwitch {
    private static final int OFFSET_OF_DEFAULT = 0;
    private static final int OFFSET_OF_NUMPAIRS = 4;
    private static final int OFFSET_OF_PAIRS = 8;

    private final int defaultTarget;
    private final List<DecodedSwitchEntry> jumpTargets;

    /*
     * Note that offsetOfOriginalInstruction is data[-1]
     */
    public DecodedLookupSwitch(byte[] data, int offsetOfOriginalInstruction) {
        int curoffset = offsetOfOriginalInstruction + 1;
        int overflow = (curoffset % 4);
        int offset = overflow > 0 ? 4 - overflow : 0;

        ByteData bd = new BaseByteData(data);
        int defaultvalue = bd.getU4At(offset + OFFSET_OF_DEFAULT);
        int numpairs = bd.getU4At(offset + OFFSET_OF_NUMPAIRS);
        int[] targets = new int[numpairs];
        this.defaultTarget = defaultvalue;
        jumpTargets = ListFactory.newList();
        for (int x = 0; x < numpairs; ++x) {
            int value = bd.getU4At(offset + OFFSET_OF_PAIRS + (x * 8));
            int target = bd.getU4At(offset + OFFSET_OF_PAIRS + (x * 8) + 4);
            if (target != defaultTarget) {
                jumpTargets.add(new DecodedSwitchEntry(value, target));
            }
        }
    }

    @Override
    public int getDefaultTarget() {
        return defaultTarget;
    }

    @Override
    public List<DecodedSwitchEntry> getJumpTargets() {
        return jumpTargets;
    }
}
