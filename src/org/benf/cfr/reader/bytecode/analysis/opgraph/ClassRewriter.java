package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.getopt.CFRState;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 07/05/2013
 * Time: 05:47
 */
public class ClassRewriter {

    public static void rewriteEnumClass(ClassFile classFile, CFRState state) {
        JavaTypeInstance classType = classFile.getClassType();
        JavaTypeInstance baseType = classFile.getBaseClassType();

        JavaTypeInstance enumType = TypeConstants.ENUM;

        if (!(baseType instanceof JavaGenericRefTypeInstance)) return;
        JavaGenericRefTypeInstance genericBaseType = (JavaGenericRefTypeInstance) baseType;
        if (!genericBaseType.getDeGenerifiedType().equals(enumType)) return;
        // It's an enum type, is it Enum<classType> ?
        List<JavaTypeInstance> boundTypes = genericBaseType.getGenericTypes();
        if (boundTypes == null || boundTypes.size() != 1) return;
        if (!boundTypes.get(0).equals(classType)) return;

        // Ok, it's an enum....
    }
}
