package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.output.Dumper;

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

    /*
     * This will return a type stripped of ALL generic information
     *
     * i.e. Set<Set<?>> -> Set
     */
    public JavaTypeInstance getDeGenerifiedType();

    /*
     * This will return a 'minimally' degenerified type - i.e. just enough to
     * remove bad generic info
     *
     * i.e. Set<Set<?>> -> Set<Set>
     */
//    public JavaTypeInstance getMinimallyDeGenerifiedType();

    public int getNumArrayDimensions();

    public String getRawName();

    //    public boolean isInnerClassOf(JavaTypeInstance possibleParent);
    // Get info about this class as an inner class, not inner classes of this.....
    public InnerClassInfo getInnerClassHereInfo();

    public BindingSuperContainer getBindingSupers();

    public boolean implicitlyCastsTo(JavaTypeInstance other);

    public boolean canCastTo(JavaTypeInstance other);

    public String suggestVarName();

    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation);

    public void collectInto(TypeUsageCollector typeUsageCollector);
}
