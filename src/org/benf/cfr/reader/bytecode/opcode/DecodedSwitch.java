package org.benf.cfr.reader.bytecode.opcode;

import java.util.List;

public interface DecodedSwitch {

    List<DecodedSwitchEntry> getJumpTargets();
}
