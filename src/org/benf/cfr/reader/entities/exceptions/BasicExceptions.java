package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/*
 * This defines the set of exceptions which can be thrown by jvm instructions natively.
 */
public class BasicExceptions {
    public static Set<? extends JavaTypeInstance> instances = SetFactory.newSet(
            JavaRefTypeInstance.createTypeConstant("java.lang.AbstractMethodError", "AbstractMethodError"),
            JavaRefTypeInstance.createTypeConstant("java.lang.ArithmeticException", "ArithmeticException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.ArrayIndexOutOfBoundsException", "ArrayIndexOutOfBoundsException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.ArrayStoreException", "ArrayStoreException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.ClassCastException", "ClassCastException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.IllegalAccessError", "IllegalAccessError"),
            JavaRefTypeInstance.createTypeConstant("java.lang.IllegalMonitorStateException", "IllegalMonitorStateException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.IncompatibleClassChangeError", "IncompatibleClassChangeError"),
            JavaRefTypeInstance.createTypeConstant("java.lang.InstantiationError", "InstantiationError"),
            JavaRefTypeInstance.createTypeConstant("java.lang.NegativeArraySizeException", "NegativeArraySizeException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.NullPointerException", "NullPointerException"),
            JavaRefTypeInstance.createTypeConstant("java.lang.UnsatisfiedLinkError", "UnsatisfiedLinkError")
    );
}
