package org.benf.cfr.reader.mapping;

import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.state.DetectedStaticImport;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.state.TypeUsageInformationImpl;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.output.DelegatingDumper;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Mapping implements ObfuscationMapping {
    // NB: This is a map of *erased* types.  If they type we're reconstructing is generic, we
    // need to reconstruct it.
    private final Map<JavaTypeInstance, ClassMapping> erasedTypeMap = MapFactory.newMap();
    private final UnaryFunction<JavaTypeInstance, JavaTypeInstance> getter = new UnaryFunction<JavaTypeInstance, JavaTypeInstance>() {
        @Override
        public JavaTypeInstance invoke(JavaTypeInstance arg) {
            return get(arg);
        }
    };
    private Options options;
    private Map<JavaTypeInstance, List<InnerClassAttributeInfo>> innerInfo;

    Mapping(Options options, List<ClassMapping> classMappings, Map<JavaTypeInstance, List<InnerClassAttributeInfo>> innerInfo) {
        this.options = options;
        this.innerInfo = innerInfo;
        for (ClassMapping cls : classMappings) {
            erasedTypeMap.put(cls.getObClass(), cls);
        }
    }

    @Override
    public Dumper wrap(Dumper d) {
        return new ObfuscationWrappingDumper(d);
    }

    @Override
    public boolean providesInnerClassInfo() {
        return true;
    }

    @Override
    public JavaTypeInstance get(JavaTypeInstance type) {
        if (type == null) return null;
        int numDim = type.getNumArrayDimensions();
        JavaTypeInstance strippedType = type.getArrayStrippedType();
        ClassMapping c = erasedTypeMap.get(strippedType);
        if (c == null) {
            return type;
        }
        JavaTypeInstance res = c.getRealClass();
        if (numDim > 0) {
            res = new JavaArrayTypeInstance(numDim, res);
        }
        return res;
    }

    @Override
    public List<JavaTypeInstance> get(List<JavaTypeInstance> types) {
        return Functional.map(types, new UnaryFunction<JavaTypeInstance, JavaTypeInstance>() {
            @Override
            public JavaTypeInstance invoke(JavaTypeInstance arg) {
                return get(arg);
            }
        });
    }

    ClassMapping getClassMapping(JavaTypeInstance type) {
        return erasedTypeMap.get(type.getDeGenerifiedType());
    }

    @Override
    public List<InnerClassAttributeInfo> getInnerClassInfo(JavaTypeInstance classType) {
        return innerInfo.get(classType);
    }

    @Override
    public UnaryFunction<JavaTypeInstance, JavaTypeInstance> getter() {
        return getter;
    }

    private class MappingTypeUsage implements TypeUsageInformation {
        private final TypeUsageInformation delegateRemapped;
        private final TypeUsageInformation delegateOriginal;

        private MappingTypeUsage(TypeUsageInformation delegateRemapped, TypeUsageInformation delegateOriginal) {
            this.delegateRemapped = delegateRemapped;
            this.delegateOriginal = delegateOriginal;
        }

        @Override
        public IllegalIdentifierDump getIid() {
            return delegateOriginal.getIid();
        }

        @Override
        public boolean isStaticImport(JavaTypeInstance clazz, String fixedName) {
            return delegateOriginal.isStaticImport(clazz, fixedName);
        }

        @Override
        public Set<DetectedStaticImport> getDetectedStaticImports() {
            return delegateOriginal.getDetectedStaticImports();
        }

        @Override
        public JavaRefTypeInstance getAnalysisType() {
            return delegateRemapped.getAnalysisType();
        }

        @Override
        public Set<JavaRefTypeInstance> getShortenedClassTypes() {
            return delegateRemapped.getShortenedClassTypes();
        }

        @Override
        public Set<JavaRefTypeInstance> getUsedClassTypes() {
            return delegateOriginal.getUsedClassTypes();
        }

        @Override
        public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
            return delegateOriginal.getUsedClassTypes();
        }

        @Override
        public String getName(JavaTypeInstance type) {
            return delegateRemapped.getName(get(type));
        }

        @Override
        public boolean hasLocalInstance(JavaRefTypeInstance type) {
            return delegateOriginal.hasLocalInstance(type);
        }

        @Override
        public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
            return delegateRemapped.generateInnerClassShortName((JavaRefTypeInstance)get(clazz));
        }

        @Override
        public String generateOverriddenName(JavaRefTypeInstance clazz) {
            return delegateRemapped.generateOverriddenName(clazz);
        }
    }

    private class ObfuscationWrappingDumper extends DelegatingDumper {
        private TypeUsageInformation mappingTypeUsage;

        private ObfuscationWrappingDumper(Dumper delegate) {
            super(delegate);
            this.mappingTypeUsage = null;
        }

        private ObfuscationWrappingDumper(Dumper delegate, TypeUsageInformation typeUsageInformation) {
            super(delegate);
            this.mappingTypeUsage = typeUsageInformation;
        }

        @Override
        public TypeUsageInformation getTypeUsageInformation() {
            if (mappingTypeUsage == null) {
                TypeUsageInformation dti = delegate.getTypeUsageInformation();
                TypeUsageInformation dtr = new TypeUsageInformationImpl(options,
                        (JavaRefTypeInstance)get(dti.getAnalysisType()),
                        SetFactory.newOrderedSet(Functional.map(dti.getUsedClassTypes(), new UnaryFunction<JavaRefTypeInstance, JavaRefTypeInstance>() {
                            @Override
                            public JavaRefTypeInstance invoke(JavaRefTypeInstance arg) {
                                return (JavaRefTypeInstance)get(arg);
                            }
                        })), SetFactory.<DetectedStaticImport>newSet());
                mappingTypeUsage = new MappingTypeUsage(dtr, dti);
            }
            return mappingTypeUsage;
        }

        @Override
        public ObfuscationMapping getObfuscationMapping() {
            return Mapping.this;
        }

        @Override
        public Dumper methodName(String s, MethodPrototype p, boolean special, boolean defines) {
            ClassMapping c = erasedTypeMap.get(p.getClassType().getDeGenerifiedType());
            if (c == null || special) {
                delegate.methodName(s, p, special, defines);
                return this;
            }

            delegate.methodName(c.getMethodName(s, p.getSignatureBoundArgs(), Mapping.this, delegate), p, special, defines);
            return this;
        }

        @Override
        public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines) {
            JavaTypeInstance deGenerifiedType = owner.getDeGenerifiedType();
            ClassMapping c = erasedTypeMap.get(deGenerifiedType);
            if (c == null || hiddenDeclaration) {
                delegate.fieldName(name, owner, hiddenDeclaration, isStatic, defines);
            } else {
                delegate.fieldName(c.getFieldName(name, deGenerifiedType,this, Mapping.this, isStatic), owner, hiddenDeclaration, isStatic, defines);
            }
            return this;
        }

        @Override
        public Dumper packageName(JavaRefTypeInstance t) {
            JavaTypeInstance deGenerifiedType = t.getDeGenerifiedType();
            ClassMapping c = erasedTypeMap.get(deGenerifiedType);
            if (c == null) {
                delegate.packageName(t);
            } else {
                delegate.packageName(c.getRealClass());
            }
            return this;
        }

        @Override
        public Dumper dump(JavaTypeInstance javaTypeInstance) {
            javaTypeInstance = javaTypeInstance.deObfuscate(Mapping.this);
            javaTypeInstance.dumpInto(this, getTypeUsageInformation());
            return this;
        }

        @Override
        public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
            return new ObfuscationWrappingDumper(delegate, innerclassTypeUsageInformation);
        }
    }
}
