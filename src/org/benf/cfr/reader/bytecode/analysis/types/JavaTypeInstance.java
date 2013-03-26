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

    /*
     * TODO : Doesn't feel like this is right, it ignores array dimensionality.
     */
    public RawJavaType getRawTypeOfSimpleType();

    /*
     * Again, can't we already be sure we have an array type here?
     * TODO : Doesn't feel right.
     */
    public JavaTypeInstance removeAnArrayIndirection();

    public JavaTypeInstance getArrayStrippedType();

    public JavaTypeInstance getDeGenerifiedType();

    public int getNumArrayDimensions();

    public String getRawName();

    public boolean isInnerClassOf(JavaTypeInstance possibleParent);

    public boolean isInnerClass();
}
