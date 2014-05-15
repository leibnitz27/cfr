package org.benf.cfr.reader.bytecode.opcode;

public class OperationFactoryLDC2W extends OperationFactoryLDCW {
    @Override
    protected int getRequiredComputationCategory() {
        return 2;
    }
}
