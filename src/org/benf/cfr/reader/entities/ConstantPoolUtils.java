package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.LoggerFactory;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:09
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolUtils {

    private static final Logger logger = LoggerFactory.create(ConstantPoolUtils.class);

    public static JavaTypeInstance decodeTypeTok(String tok, ConstantPool cp) {
        int idx = 0;
        int numArrayDims = 0;
        char c = tok.charAt(idx);
        while (c == '[') {
            numArrayDims++;
            c = tok.charAt(++idx);
        }
        JavaTypeInstance javaTypeInstance = null;
        switch (c) {
            case 'L':   // object
                javaTypeInstance = new JavaRefTypeInstance(tok.substring(idx + 1, tok.length() - 1));
                break;
            case 'B':   // byte
                javaTypeInstance = RawJavaType.BYTE;
                break;
            case 'C':   // char
                javaTypeInstance = RawJavaType.CHAR;
                break;
            case 'I':   // integer
                javaTypeInstance = RawJavaType.INT;
                break;
            case 'S':   // short
                javaTypeInstance = RawJavaType.SHORT;
                break;
            case 'Z':   // boolean
                javaTypeInstance = RawJavaType.BOOLEAN;
                break;
            case 'F':   // float
                javaTypeInstance = RawJavaType.FLOAT;
                break;
            case 'D':   // double
                javaTypeInstance = RawJavaType.DOUBLE;
                break;
            case 'J':   // long
                javaTypeInstance = RawJavaType.LONG;
                break;
            default:
                throw new ConfusedCFRException("Invalid type string " + tok);
        }
        if (numArrayDims > 0) javaTypeInstance = new JavaArrayTypeInstance(numArrayDims, javaTypeInstance);
        return javaTypeInstance;
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

    public static MethodPrototype parseJavaMethodPrototype(boolean instanceMethod, ConstantPoolEntryUTF8 prototype, ConstantPool cp, VariableNamer variableNamer) {
        String proto = prototype.getValue();
        int curridx = 1;
        if (!proto.startsWith("(")) throw new ConfusedCFRException("Prototype " + proto + " is invalid");
        List<JavaTypeInstance> args = ListFactory.newList();
        while (proto.charAt(curridx) != ')') {
            String typeTok = getNextTypeTok(proto, curridx);
            args.add(decodeTypeTok(typeTok, cp));
            curridx += typeTok.length();
        }
        curridx++;
        JavaTypeInstance resultType = RawJavaType.VOID;
        switch (proto.charAt(curridx)) {
            case 'V':
                break;
            default:
                resultType = decodeTypeTok(getNextTypeTok(proto, curridx), cp);
                break;
        }
        MethodPrototype res = new MethodPrototype(instanceMethod, args, resultType, variableNamer);
        logger.info("Parsed prototype " + proto + " as " + res);
        return res;
    }


    /*
     * could be rephrased in terms of MethodPrototype.
     */
    public static StackDelta parseMethodPrototype(boolean member, ConstantPoolEntryUTF8 prototype, ConstantPool cp) {
        String proto = prototype.getValue();
        int curridx = 1;
        if (!proto.startsWith("(")) throw new ConfusedCFRException("Prototype " + proto + " is invalid");
        StackTypes argumentTypes = new StackTypes();
        if (member) {
            argumentTypes.add(StackType.REF); // thisPtr
        }
        while (proto.charAt(curridx) != ')') {
            String typeTok = getNextTypeTok(proto, curridx);
            argumentTypes.add(decodeTypeTok(typeTok, cp).getStackType());
            curridx += typeTok.length();
        }
        curridx++;
        StackTypes resultType = StackTypes.EMPTY; // void.
        switch (proto.charAt(curridx)) {
            case 'V':
                break;
            default:
                resultType = decodeTypeTok(getNextTypeTok(proto, curridx), cp).getStackType().asList();
                break;
        }
        StackDelta res = new StackDelta(argumentTypes, resultType);
        logger.info("Parsed prototype " + proto + " as " + res);
        return res;
    }
}
