package org.benf.cfr.reader.bytecode.opcode;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public class OperationFactoryLDC2W extends OperationFactoryLDCW {
    @Override
    protected int getRequiredComputationCategory() {
        return 2;
    }
}
