package org.benf.cfr.reader.bytecode.opcode;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/04/2012
 */
public interface DecodedSwitch {

    int getDefaultTarget();

    List<DecodedSwitchEntry> getJumpTargets();

}
