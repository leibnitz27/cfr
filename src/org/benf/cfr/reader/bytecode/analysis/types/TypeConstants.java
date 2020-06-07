package org.benf.cfr.reader.bytecode.analysis.types;

public interface TypeConstants {
    String objectsName = "java.util.Objects";
    String throwableName = "java.lang.Throwable";
    String stringName = "java.lang.String";
    String charSequenceName = "java.lang.CharSequence";
    String stringBuilderName = "java.lang.StringBuilder";
    String stringBufferName = "java.lang.StringBuffer";
    String className = "java.lang.Class";
    String objectName = "java.lang.Object";
    
    String methodHandlesName = "java.lang.invoke.MethodHandles";
    String methodHandlesLookupName = "java.lang.invoke.MethodHandles$Lookup";
    String methodTypeName = "java.lang.invoke.MethodType";
    String lambdaMetaFactoryName = "java.lang.invoke.LambdaMetafactory";
    String stringConcatFactoryName = "java.lang.invoke.StringConcatFactory";
    
    JavaRefTypeInstance OBJECT = JavaRefTypeInstance.createTypeConstant(objectName);
    JavaRefTypeInstance ENUM = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.lang.Enum");
    JavaRefTypeInstance ASSERTION_ERROR = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.lang.AssertionError");
    JavaRefTypeInstance CHAR_SEQUENCE = JavaRefTypeInstance.createTypeConstantWithObjectSuper(charSequenceName);
    JavaRefTypeInstance STRING = JavaRefTypeInstance.createTypeConstant(stringName, OBJECT, CHAR_SEQUENCE);
    JavaRefTypeInstance CLASS = JavaRefTypeInstance.createTypeConstantWithObjectSuper(className);
    JavaRefTypeInstance ITERABLE = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.lang.Iterable");
    JavaRefTypeInstance CLOSEABLE = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.io.Closeable");
    JavaRefTypeInstance SERIALIZABLE = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.io.Serializable");
    JavaRefTypeInstance THROWABLE = JavaRefTypeInstance.createTypeConstantWithObjectSuper(throwableName);
    JavaRefTypeInstance AUTO_CLOSEABLE = JavaRefTypeInstance.createTypeConstant("java.lang.AutoCloseable");
    JavaRefTypeInstance SUPPLIER = JavaRefTypeInstance.createTypeConstant("java.util.function.Supplier");
    JavaRefTypeInstance SCALA_SIGNATURE = JavaRefTypeInstance.createTypeConstant("scala.reflect.ScalaSignature");
    JavaRefTypeInstance NOCLASSDEFFOUND_ERROR = JavaRefTypeInstance.createTypeConstant("java.lang.NoClassDefFoundError");
    JavaRefTypeInstance COMPARABLE = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.lang.Comparable");
    JavaRefTypeInstance MATH = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.lang.Math");
    JavaRefTypeInstance OVERRIDE = JavaRefTypeInstance.createTypeConstantWithObjectSuper("java.lang.Override");
    JavaRefTypeInstance RECORD = JavaRefTypeInstance.createTypeConstant("java.lang.Record");
    JavaRefTypeInstance OBJECTMETHODS = JavaRefTypeInstance.createTypeConstant("java.lang.runtime.ObjectMethods");
    JavaRefTypeInstance METHOD_HANDLES = JavaRefTypeInstance.createTypeConstantWithObjectSuper(methodHandlesName);
    JavaRefTypeInstance METHOD_TYPE = JavaRefTypeInstance.createTypeConstantWithObjectSuper(methodTypeName);
    
    String boxingNameBoolean = "java.lang.Boolean";
    String boxingNameByte = "java.lang.Byte";
    String boxingNameShort = "java.lang.Short";
    String boxingNameChar = "java.lang.Character";
    String boxingNameInt = "java.lang.Integer";
    String boxingNameLong = "java.lang.Long";
    String boxingNameFloat = "java.lang.Float";
    String boxingNameDouble = "java.lang.Double";
    String boxingNameNumber = "java.lang.Number";

    JavaRefTypeInstance NUMBER = JavaRefTypeInstance.createTypeConstant(boxingNameNumber, OBJECT, SERIALIZABLE);
    JavaRefTypeInstance INTEGER = JavaRefTypeInstance.createTypeConstant(boxingNameInt, NUMBER, COMPARABLE);
    JavaRefTypeInstance SHORT = JavaRefTypeInstance.createTypeConstant(boxingNameShort, NUMBER, COMPARABLE);
    JavaRefTypeInstance LONG = JavaRefTypeInstance.createTypeConstant(boxingNameLong, NUMBER, COMPARABLE);
    JavaRefTypeInstance DOUBLE = JavaRefTypeInstance.createTypeConstant(boxingNameDouble, NUMBER, COMPARABLE);
    JavaRefTypeInstance FLOAT = JavaRefTypeInstance.createTypeConstant(boxingNameFloat, NUMBER, COMPARABLE);

    // Path, because we actually want to load the class - could we get away with a hardcoded ref type as above?
    String runtimeExceptionPath = "java/lang/RuntimeException.class";
}
