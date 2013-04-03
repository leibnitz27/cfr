package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.bytecode.analysis.stack.StackDeltaImpl;
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

    private static JavaTypeInstance parseRefType(String tok, ConstantPool cp, boolean isTemplate) {
        int idxGen = tok.indexOf('<');
        if (idxGen != -1) {
            String pre = tok.substring(0, idxGen);
            String gen = tok.substring(idxGen + 1, tok.length() - 1);
            JavaRefTypeInstance clazzType = cp.getClassCache().getRefClassFor(pre);
            List<JavaTypeInstance> genericTypes = parseTypeList(gen, cp);
            return new JavaGenericRefTypeInstance(clazzType, genericTypes, cp);
        } else if (isTemplate) {
            return new JavaGenericPlaceholderTypeInstance(tok, cp);
        } else {
            return cp.getClassCache().getRefClassFor(tok);
        }
    }

    public static JavaTypeInstance decodeTypeTok(String tok, ConstantPool cp) {
        int idx = 0;
        int numArrayDims = 0;
        char c = tok.charAt(idx);
        WildcardType wildcardType = WildcardType.NONE;
        if (c == '-' || c == '+') {
            wildcardType = c == '+' ? WildcardType.EXTENDS : WildcardType.SUPER;
            c = tok.charAt(++idx);
        }
        while (c == '[') {
            numArrayDims++;
            c = tok.charAt(++idx);
        }
        JavaTypeInstance javaTypeInstance = null;
        switch (c) {
            case '*': // wildcard
                javaTypeInstance = new JavaGenericPlaceholderTypeInstance("?", cp);
                break;
            case 'L':   // object
                javaTypeInstance = parseRefType(tok.substring(idx + 1, tok.length() - 1), cp, false);
                break;
            case 'T':   // Template
                javaTypeInstance = parseRefType(tok.substring(idx + 1, tok.length() - 1), cp, true);
                break;
            case 'B':   // byte
            case 'C':   // char
            case 'I':   // integer
            case 'S':   // short
            case 'Z':   // boolean
            case 'F':   // float
            case 'D':   // double
            case 'J':   // long
                javaTypeInstance = decodeRawJavaType(c);
                break;
            default:
                throw new ConfusedCFRException("Invalid type string " + tok);
        }
        if (numArrayDims > 0) javaTypeInstance = new JavaArrayTypeInstance(numArrayDims, javaTypeInstance);
        if (wildcardType != WildcardType.NONE) {
            javaTypeInstance = new JavaWildcardTypeInstance(wildcardType, javaTypeInstance);
        }
        return javaTypeInstance;
    }

    public static RawJavaType decodeRawJavaType(char c) {
        RawJavaType javaTypeInstance;
        switch (c) {
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
                throw new ConfusedCFRException("Illegal raw java type");
        }
        return javaTypeInstance;
    }

    private static String getNextTypeTok(String proto, int curridx) {
        final int startidx = curridx;
        char c = proto.charAt(curridx);

        if (c == '-' || c == '+') {
            c = proto.charAt(++curridx);
        }

        while (c == '[') {
            c = proto.charAt(++curridx);
        }

        switch (c) {
            case '*':   // wildcard
                curridx++;
                break;
            case 'L':
            case 'T': {
                int openBra = 0;
                do {
                    c = proto.charAt(++curridx);
                    switch (c) {
                        case '<':
                            openBra++;
                            break;
                        case '>':
                            openBra--;
                            break;
                    }
                } while (openBra > 0 || c != ';');
                curridx++;
                break;
            }
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
                throw new ConfusedCFRException("Can't parse proto : " + proto + " starting " + proto.substring(startidx));
        }
        return proto.substring(startidx, curridx);
    }

    private static String getNextFormalTypeTok(String proto, int curridx) {
        final int startidx = curridx;

        while (proto.charAt(curridx) != ':') {
            curridx++;
        }
        curridx++;
        if (proto.charAt(curridx) != ':') {
            // Class bound.
            String classBound = getNextTypeTok(proto, curridx);
            curridx += classBound.length();
        }
        if (proto.charAt(curridx) == ':') {
            // interface bound
            curridx++;
            String interfaceBound = getNextTypeTok(proto, curridx);
            curridx += interfaceBound.length();
        }
        return proto.substring(startidx, curridx);
    }

    private static FormalTypeParameter decodeFormalTypeTok(String tok, ConstantPool cp, int idx) {

        while (tok.charAt(idx) != ':') {
            idx++;
        }
        String name = tok.substring(0, idx);
        idx++;
        JavaTypeInstance classBound = null;
        if (tok.charAt(idx) != ':') {
            // Class bound.
            String classBoundTok = getNextTypeTok(tok, idx);
            classBound = decodeTypeTok(classBoundTok, cp);
            idx += classBoundTok.length();
        }
        JavaTypeInstance interfaceBound = null;
        if (idx < tok.length()) {
            if (tok.charAt(idx) == ':') {
                // interface bound
                idx++;
                String interfaceBoundTok = getNextTypeTok(tok, idx);
                interfaceBound = decodeTypeTok(interfaceBoundTok, cp);
                idx += interfaceBoundTok.length();
            }
        }
        return new FormalTypeParameter(name, classBound, interfaceBound);
    }

    public static ClassSignature parseClassSignature(ConstantPoolEntryUTF8 signature, ConstantPool cp) {
        String sig = signature.getValue();
        int curridx = 0;

        /*
         * Optional formal type parameters
         */
        List<FormalTypeParameter> formalTypeParameters = null;
        if (sig.charAt(curridx) == '<') {
            formalTypeParameters = ListFactory.newList();
            curridx++;
            while (sig.charAt(curridx) != '>') {
                String formalTypeTok = getNextFormalTypeTok(sig, curridx);
                formalTypeParameters.add(decodeFormalTypeTok(formalTypeTok, cp, 0));
                curridx += formalTypeTok.length();
            }
            curridx++;
        }
        /*
         * Superclass signature.
         */
        String superClassSignatureTok = getNextTypeTok(sig, curridx);
        curridx += superClassSignatureTok.length();
        JavaTypeInstance superClassSignature = decodeTypeTok(superClassSignatureTok, cp);

        List<JavaTypeInstance> interfaceClassSignatures = ListFactory.newList();
        while (curridx < sig.length()) {
            String interfaceSignatureTok = getNextTypeTok(sig, curridx);
            curridx += interfaceSignatureTok.length();
            interfaceClassSignatures.add(decodeTypeTok(interfaceSignatureTok, cp));
        }

        return new ClassSignature(formalTypeParameters, superClassSignature, interfaceClassSignatures);
    }

    public static MethodPrototype parseJavaMethodPrototype(ClassFile classFile, String name, boolean instanceMethod, ConstantPoolEntryUTF8 prototype, ConstantPool cp, boolean varargs, VariableNamer variableNamer) {
        String proto = prototype.getValue();
        int curridx = 0;
        /*
         * Method is itself generic...
         */
        List<FormalTypeParameter> formalTypeParameters = null;
        if (proto.charAt(curridx) == '<') {
            formalTypeParameters = ListFactory.newList();
            curridx++;
            while (proto.charAt(curridx) != '>') {
                String formalTypeTok = getNextFormalTypeTok(proto, curridx);
                formalTypeParameters.add(decodeFormalTypeTok(formalTypeTok, cp, 0));
                curridx += formalTypeTok.length();
            }
            curridx++;
        }
        if (proto.charAt(curridx) != '(') throw new ConfusedCFRException("Prototype " + proto + " is invalid");
        curridx++;
        List<JavaTypeInstance> args = ListFactory.newList();
        // could use parseTypeList below.
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
        MethodPrototype res = new MethodPrototype(classFile, name, instanceMethod, formalTypeParameters, args, resultType, varargs, variableNamer, cp);
//        logger.info("Parsed prototype " + proto + " as " + res);
        return res;
    }

    public static List<JavaTypeInstance> parseTypeList(String proto, ConstantPool cp) {
        int curridx = 0;
        int len = proto.length();
        List<JavaTypeInstance> res = ListFactory.newList();
        while (curridx < len) {
            String typeTok = getNextTypeTok(proto, curridx);
            res.add(decodeTypeTok(typeTok, cp));
            curridx += typeTok.length();
        }
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
        StackDelta res = new StackDeltaImpl(argumentTypes, resultType);
//        logger.info("Parsed prototype " + proto + " as " + res);
        return res;
    }
}
