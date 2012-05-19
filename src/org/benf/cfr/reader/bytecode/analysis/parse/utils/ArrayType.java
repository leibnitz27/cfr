package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 29/03/2012
 * Time: 13:24
 * To change this template use File | Settings | File Templates.
 */
public enum ArrayType {
    T_BOOLEAN(4,"boolean"),
    T_CHAR(5,"char"),
    T_FLOAT(6,"float"),
    T_DOUBLE(7,"double"),
    T_BYTE(8,"byte"),
    T_SHORT(9,"short"),
    T_INT(10,"int"),
    T_LONG(11,"long");
    
    private final int spec;
    private final String name;
    
    ArrayType(int spec, String name) {
        this.spec = spec;
        this.name = name;
    }
    
    
    
    public static ArrayType getArrayType(int id) {
        switch (id) {
            case 4:
                return T_BOOLEAN;
            case 5:
                return T_CHAR;
            case 6:
                return T_FLOAT;
            case 7:
                return T_DOUBLE;
            case 8:
                return T_BYTE;
            case 9:
                return T_SHORT;
            case 10:
                return T_INT;
            case 11:
                return T_LONG;
            default:
                throw new ConfusedCFRException("No such primitive array type " + id);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}