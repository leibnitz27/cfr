package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/07/2012
 * Time: 20:44
 */
public interface TypeConstants {
    public final static JavaRefTypeInstance ASSERTION_ERROR = JavaRefTypeInstance.createTypeConstant("java.lang.AssertionError", "AssertionError");
    public final static JavaRefTypeInstance CLASS = JavaRefTypeInstance.createTypeConstant("java.lang.Class", "Class");
    public final static JavaRefTypeInstance ENUM = JavaRefTypeInstance.createTypeConstant("java.lang.Enum", "Enum");
    public final static JavaRefTypeInstance OBJECT = JavaRefTypeInstance.createTypeConstant("java.lang.Object", "Object");
    public final static JavaRefTypeInstance STRING = JavaRefTypeInstance.createTypeConstant("java.lang.String", "String");
}
