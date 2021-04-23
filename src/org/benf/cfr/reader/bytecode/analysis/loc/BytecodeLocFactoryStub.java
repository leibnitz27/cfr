package org.benf.cfr.reader.bytecode.analysis.loc;

import org.benf.cfr.reader.entities.Method;

public class BytecodeLocFactoryStub implements BytecodeLocFactory {
    public static BytecodeLocFactory INSTANCE = new BytecodeLocFactoryStub();

    private BytecodeLocFactoryStub() {
    }

    @Override
    public BytecodeLoc at(int originalRawOffset, Method method) {
        return BytecodeLocFactory.DISABLED;
    }
}
