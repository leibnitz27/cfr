package org.benf.cfr.reader.bytecode.opcode;

import com.sun.org.apache.bcel.internal.generic.RETURN;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/04/2012
 */
public class DecodedTableSwitch implements DecodedSwitch {
    private static final int OFFSET_OF_DEFAULT = 0;
    private static final int OFFSET_OF_LOWBYTE = 4;
    private static final int OFFSET_OF_HIGHBYTE = 8;
    private static final int OFFSET_OF_OFFSETS = 12;

    private final int startValue;
    private final int endValue;
    private final int defaultTarget;
    private final List<DecodedSwitchEntry> jumpTargets;

    /*
     * Note that offsetOfOriginalInstruction is data[-1]
     */
    public DecodedTableSwitch(byte[] data, int offsetOfOriginalInstruction) {
        int curoffset = offsetOfOriginalInstruction + 1;
        int overflow = (curoffset % 4);
        int offset = overflow > 0 ? 4 - overflow : 0;

        ByteData bd = new BaseByteData(data);
        int defaultvalue = bd.getU4At(offset + OFFSET_OF_DEFAULT);
        int lowvalue = bd.getU4At(offset + OFFSET_OF_LOWBYTE);
        int highvalue = bd.getU4At(offset + OFFSET_OF_HIGHBYTE);
        int numoffsets = highvalue - lowvalue + 1;
        int[] targets = new int[numoffsets];
        for (int x = 0; x < numoffsets; ++x) {
            targets[x] = bd.getU4At(offset + OFFSET_OF_OFFSETS + (x * 4));
        }
        this.defaultTarget = defaultvalue;
        this.startValue = lowvalue;
        this.endValue = highvalue;

        jumpTargets = ListFactory.newList();

        for (int x = 0; x < targets.length; ++x) {
            if (targets[x] != defaultTarget) {
                jumpTargets.add(new DecodedSwitchEntry(x + startValue, targets[x]));
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
