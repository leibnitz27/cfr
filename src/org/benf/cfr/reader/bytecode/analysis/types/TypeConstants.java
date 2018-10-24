package org.benf.cfr.reader.bytecode.analysis.types;

public interface TypeConstants {
    JavaRefTypeInstance OBJECT = JavaRefTypeInstance.createTypeConstant("java.lang.Object", "Object");
    JavaRefTypeInstance ENUM = JavaRefTypeInstance.createTypeConstant("java.lang.Enum", "Enum", OBJECT);
    JavaRefTypeInstance ASSERTION_ERROR = JavaRefTypeInstance.createTypeConstant("java.lang.AssertionError", "AssertionError", OBJECT);
    JavaRefTypeInstance CHAR_SEQUENCE = JavaRefTypeInstance.createTypeConstant("java.lang.CharSequence", "CharSequence", OBJECT);
    JavaRefTypeInstance STRING = JavaRefTypeInstance.createTypeConstant("java.lang.String", "String", OBJECT, CHAR_SEQUENCE);
    JavaRefTypeInstance CLASS = JavaRefTypeInstance.createTypeConstant("java.lang.Class", "Class", OBJECT);
    JavaRefTypeInstance ITERABLE = JavaRefTypeInstance.createTypeConstant("java.lang.Iterable", "Iterable", OBJECT);
    JavaRefTypeInstance CLOSEABLE = JavaRefTypeInstance.createTypeConstant("java.io.Closeable", "Closeable", OBJECT);
    JavaRefTypeInstance SERIALIZABLE = JavaRefTypeInstance.createTypeConstant("java.io.Serializable", "Serializable", OBJECT);
    JavaRefTypeInstance THROWABLE = JavaRefTypeInstance.createTypeConstant("java.lang.Throwable", "Throwable", OBJECT);
    JavaRefTypeInstance AUTO_CLOSEABLE = JavaRefTypeInstance.createTypeConstant("java.lang.AutoCloseable", "AutoCloseable");
    JavaRefTypeInstance SUPPLIER = JavaRefTypeInstance.createTypeConstant("java.util.function.Supplier", "Object");
    JavaRefTypeInstance SCALA_SIGNATURE = JavaRefTypeInstance.createTypeConstant("scala.reflect.ScalaSignature", "Object");
    JavaRefTypeInstance NOCLASSDEFFOUND_ERROR = JavaRefTypeInstance.createTypeConstant("java.lang.NoClassDefFoundError", "NoClassDefFoundError");

    String boxingNameBoolean = "java.lang.Boolean";
    String boxingNameByte = "java.lang.Byte";
    String boxingNameShort = "java.lang.Short";
    String boxingNameChar = "java.lang.Character";
    String boxingNameInt = "java.lang.Integer";
    String boxingNameLong = "java.lang.Long";
    String boxingNameFloat = "java.lang.Float";
    String boxingNameDouble = "java.lang.Double";
    String boxingNameNumber = "java.lang.Number";

    String objectsName = "java.util.Objects";
    String throwableName = "java.lang.Throwable";
    String stringName = "java.lang.String";
    String charSequenceName = "java.lang.CharSequence";
    String stringBuilderName = "java.lang.StringBuilder";
    String stringBufferName = "java.lang.StringBuffer";
    String className = "java.lang.Class";
    String objectName = "java.lang.Object";

    String lambdaMetaFactoryName = "java.lang.invoke.LambdaMetafactory";
    String supplierName = "java.util.function.Supplier";
    String stringConcatFactoryName = "java.lang.invoke.StringConcatFactory";

    // Path, because we actually want to load the class - could we get away with a hardcoded ref type as above?
    String runtimeExceptionPath = "java/lang/RuntimeException.class";

}
