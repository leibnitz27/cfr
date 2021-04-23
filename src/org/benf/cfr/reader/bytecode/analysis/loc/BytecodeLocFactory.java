package org.benf.cfr.reader.bytecode.analysis.loc;

import org.benf.cfr.reader.entities.Method;

public interface BytecodeLocFactory {
    BytecodeLoc DISABLED = new BytecodeLocSpecific(BytecodeLocSpecific.Specific.DISABLED);
    BytecodeLoc NONE = new BytecodeLocSpecific(BytecodeLocSpecific.Specific.NONE);
    BytecodeLoc TODO = new BytecodeLocSpecific(BytecodeLocSpecific.Specific.TODO);

    BytecodeLoc at(int originalRawOffset, Method method);
}
