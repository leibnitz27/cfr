package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:09
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolUtils {
    public static StackDelta parseMethodPrototype(boolean member, ConstantPoolEntryUTF8 prototype) {
        String proto = prototype.getValue();
        boolean isMemberFunction = member;
        int curridx = 1;
        if (!proto.startsWith("(")) throw new ConfusedCFRException("Prototype " + proto + " is invalid");
        int numArgs = isMemberFunction ? 1 : 0;
        int numReturns = 0;
        boolean finished = false;
        do {
            char c = proto.charAt(curridx);
            switch (c) {
                case 'L':
                    numArgs++;
                    do {
                        c = proto.charAt(++curridx);
                    } while (c != ';');
                    curridx++;
                    break;
                case '[': // ignore array qualifiers for now.
                    curridx++;
                    break;
                case 'I':
                case 'B':
                case 'J':
                case 'S':
                case 'D':
                    numArgs++;
                    curridx++;
                    break;
                case ')':
                    curridx++;
                    finished = true;
                    break;
                default:
                    throw new ConfusedCFRException("Can't parse proto : " + proto);

            }
        } while (!finished);
        switch (proto.charAt(curridx)) {
            case 'V':
                numReturns = 0;
                break;
            default:
                numReturns = 1;
                break;
        }
        StackDelta res = new StackDelta(numArgs, numReturns);
        System.out.println("Parsed prototype " + proto + " as " + res);
        return res;
    }
}
