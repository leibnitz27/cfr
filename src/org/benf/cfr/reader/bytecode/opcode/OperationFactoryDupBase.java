package org.benf.cfr.reader.bytecode.opcode;

import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.StackTypes;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 21/04/2011
 * Time: 08:10
 * To change this template use File | Settings | File Templates.
 */
public abstract class OperationFactoryDupBase extends OperationFactoryDefault {
    protected StackTypes getStackTypes(StackSim stackSim, Integer... indexes) {
        if (indexes.length == 1) {
            return stackSim.getEntry(indexes[0]).getType().asList();
        } else {
            List<StackType> stackTypes = ListFactory.newList();
            for (Integer index : indexes) {
                stackTypes.add(stackSim.getEntry(index).getType());
            }
            return new StackTypes(stackTypes);
        }
    }

    protected int getCat(StackSim stackSim, int index) {
        return stackSim.getEntry(index).getType().getComputationCategory();
    }

    protected void checkCat(StackSim stackSim, int index, int category) {
        if (getCat(stackSim, index) != category) {
            throw new ConfusedCFRException("Expected category " + category + " at index " + index);
        }
    }
}
