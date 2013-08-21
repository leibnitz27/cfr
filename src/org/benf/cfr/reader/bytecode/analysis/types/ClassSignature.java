package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/02/2013
 * Time: 06:32
 */
public class ClassSignature {
    private final List<FormalTypeParameter> formalTypeParameters;
    private final JavaTypeInstance superClass;
    private final List<JavaTypeInstance> interfaces;

    public ClassSignature(List<FormalTypeParameter> formalTypeParameters, JavaTypeInstance superClass, List<JavaTypeInstance> interfaces) {
        this.formalTypeParameters = formalTypeParameters;
        this.superClass = superClass;
        this.interfaces = interfaces;
    }

    public List<FormalTypeParameter> getFormalTypeParameters() {
        return formalTypeParameters;
    }

    public JavaTypeInstance getSuperClass() {
        return superClass;
    }

    public List<JavaTypeInstance> getInterfaces() {
        return interfaces;
    }

    // TODO : This is pointless.
    public JavaTypeInstance getThisGeneralTypeClass(JavaTypeInstance nonGenericInstance, ConstantPool cp) {
        if (nonGenericInstance instanceof JavaGenericBaseInstance) return nonGenericInstance;
        if (formalTypeParameters == null || formalTypeParameters.isEmpty()) return nonGenericInstance;
        List<JavaTypeInstance> typeParameterNames = ListFactory.newList();
        for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
            typeParameterNames.add(new JavaGenericPlaceholderTypeInstance(formalTypeParameter.getName(), cp));
        }
        JavaTypeInstance res = new JavaGenericRefTypeInstance(nonGenericInstance, typeParameterNames);
        return res;
    }
}
