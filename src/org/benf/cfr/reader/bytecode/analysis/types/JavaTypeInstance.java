package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 07:57
 */
public interface JavaTypeInstance {
    StackType getStackType();

    public boolean isComplexType();

    public boolean isUsableType();

    public RawJavaType getRawTypeOfSimpleType();

    public JavaTypeInstance removeAnArrayIndirection();

    /*
     *
     */
    public String getBeforeNewString();

    /*
     *
     */
    public String getAfterNewString();
}
