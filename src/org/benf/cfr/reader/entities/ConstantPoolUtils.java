package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackType;
import org.benf.cfr.reader.bytecode.analysis.stack.StackTypes;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:09
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolUtils {

    public static StackType decodeTypeTok(String tok) {
        int idx = 0;
        int numArrayDims = 0;
        char c = tok.charAt(idx);
        while (c == '[') {
            numArrayDims++;
            c = tok.charAt(++idx);
        }
        if (numArrayDims > 0) return StackType.REF;
        switch (c) {
            case 'L':   // object
                return StackType.REF;
            case 'B':   // byte
            case 'C':   // char
            case 'I':   // integer
            case 'S':   // short
            case 'Z':   // boolean
                return StackType.INT;
            case 'F':   // float
                return StackType.FLOAT;
            case 'D':   // double
                return StackType.DOUBLE;
            case 'J':   // long
                return StackType.LONG;
            default:
                throw new ConfusedCFRException("Invalid type string " + tok);
        }
    }

    private static String getNextTypeTok(String proto, int curridx) {
        final int startidx = curridx;
        char c = proto.charAt(curridx);

        while (c == '[') {
            c = proto.charAt(++curridx);
        }

        switch (c) {
            case 'L':
                do {
                    c = proto.charAt(++curridx);
                } while (c != ';');
                curridx++;
                break;
            case 'B':   // byte
            case 'C':   // char
            case 'I':   // integer
            case 'S':   // short
            case 'Z':   // boolean
            case 'F':   // float
            case 'D':   // double
            case 'J':   // long
                curridx++;
                break;
            default:
                throw new ConfusedCFRException("Can't parse proto : " + proto);
        }
        return proto.substring(startidx, curridx);
    }

    public static StackDelta parseMethodPrototype(boolean member, ConstantPoolEntryUTF8 prototype) {
        String proto = prototype.getValue();
        int curridx = 1;
        if (!proto.startsWith("(")) throw new ConfusedCFRException("Prototype " + proto + " is invalid");
        StackTypes argumentTypes = new StackTypes();
        if (member) {
            argumentTypes.add(StackType.REF); // thisPtr
        }
        while (proto.charAt(curridx) != ')') {
            String typeTok = getNextTypeTok(proto, curridx);
            argumentTypes.add(decodeTypeTok(typeTok));
            curridx += typeTok.length();
        }
        curridx++;
        StackTypes resultType = StackTypes.EMPTY; // void.
        switch (proto.charAt(curridx)) {
            case 'V':
                break;
            default:
                resultType = decodeTypeTok(getNextTypeTok(proto, curridx)).asList();
                break;
        }
        StackDelta res = new StackDelta(argumentTypes, resultType);
        System.out.println("Parsed prototype " + proto + " as " + res);
        return res;
    }
}
