package org.benf.cfr.reader.mapping;

import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.ObfuscationMapping;
import org.benf.cfr.reader.state.ObfuscationRewriter;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.state.TypeUsageInformationImpl;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Mapping implements ObfuscationRewriter, ObfuscationMapping {
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

    Mapping(Options options, List<ClassMapping> classMappings) {
        this.options = options;
        for (ClassMapping cls : classMappings) {
            erasedTypeMap.put(cls.getObClass(), cls);
        }
    }

    @Override
    public Dumper wrap(Dumper d) {
        return new ObfuscationWrappingDumper(d);
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

    public ClassMapping getClassMapping(JavaTypeInstance type) {
        return erasedTypeMap.get(type.getDeGenerifiedType());
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
        public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
            return delegateRemapped.generateInnerClassShortName((JavaRefTypeInstance)get(clazz));
        }

        @Override
        public String generateOverriddenName(JavaRefTypeInstance clazz) {
            return delegateRemapped.generateOverriddenName(clazz);
        }
    }

    private class ObfuscationWrappingDumper implements Dumper {
        private final Dumper delegate;
        private TypeUsageInformation mappingTypeUsage;

        private ObfuscationWrappingDumper(Dumper delegate) {
            this.delegate = delegate;
            this.mappingTypeUsage = null;
        }

        private ObfuscationWrappingDumper(Dumper delegate, TypeUsageInformation typeUsageInformation) {
            this.delegate = delegate;
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
                        })));
                mappingTypeUsage = new MappingTypeUsage(dtr, dti);
            }
            return mappingTypeUsage;
        }

        @Override
        public void printLabel(String s) {
            delegate.printLabel(s);
        }

        @Override
        public void enqueuePendingCarriageReturn() {
            delegate.enqueuePendingCarriageReturn();
        }

        @Override
        public Dumper removePendingCarriageReturn() {
            delegate.removePendingCarriageReturn();
            return this;
        }

        @Override
        public Dumper print(String s) {
            delegate.print(s);
            return this;
        }

        @Override
        public Dumper methodName(String s, MethodPrototype p, boolean special) {
            ClassMapping c = erasedTypeMap.get(p.getClassType().getDeGenerifiedType());
            if (c == null || special) {
                delegate.methodName(s, p, special);
                return this;
            }

            delegate.methodName(c.getMethodName(s, p.getSignatureBoundArgs(), Mapping.this, delegate), p, special);
            return this;
        }

        @Override
        public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic) {
            JavaTypeInstance deGenerifiedType = owner.getDeGenerifiedType();
            ClassMapping c = erasedTypeMap.get(deGenerifiedType);
            if (c == null || hiddenDeclaration) {
                delegate.fieldName(name, owner, hiddenDeclaration, isStatic);
            } else {
                delegate.fieldName(c.getFieldName(name, deGenerifiedType,this, Mapping.this, isStatic), owner, hiddenDeclaration, isStatic);
            }
            return this;
        }

        @Override
        public Dumper identifier(String s) {
            delegate.identifier(s);
            return this;
        }

        @Override
        public Dumper print(char c) {
            delegate.print(c);
            return this;
        }

        @Override
        public Dumper newln() {
            delegate.newln();
            return this;
        }

        @Override
        public Dumper endCodeln() {
            delegate.endCodeln();
            return this;
        }

        @Override
        public int getIndent() {
            return delegate.getIndent();
        }

        @Override
        public void indent(int diff) {
            delegate.indent(diff);
        }

        @Override
        public void dump(List<? extends Dumpable> d) {
            delegate.dump(d);
        }

        @Override
        public Dumper dump(JavaTypeInstance javaTypeInstance) {
            javaTypeInstance = javaTypeInstance.deObfuscate(Mapping.this);
            javaTypeInstance.dumpInto(this, getTypeUsageInformation());
            return this;
        }

        @Override
        public Dumper dump(Dumpable d) {
            // can't delegate, because it passes self.
            if (d == null) {
                return print("null");
            }
            return d.dump(this);
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public void addSummaryError(Method method, String s) {
            delegate.addSummaryError(method, s);
        }

        @Override
        public boolean canEmitClass(JavaTypeInstance type) {
            return delegate.canEmitClass(type);
        }

        @Override
        public int getOutputCount() {
            return delegate.getOutputCount();
        }

        @Override
        public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
            return new ObfuscationWrappingDumper(delegate, innerclassTypeUsageInformation);
        }
    }
}
