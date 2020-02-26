package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;

public class TypeOverridingDumper extends DelegatingDumper {
    private final TypeUsageInformation typeUsageInformation;

    TypeOverridingDumper(Dumper delegate, TypeUsageInformation typeUsageInformation) {
        super(delegate);
        this.typeUsageInformation = typeUsageInformation;
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        return dump(javaTypeInstance, TypeContext.None);
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext) {
        javaTypeInstance.dumpInto(this, typeUsageInformation, typeContext);
        return this;
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TypeOverridingDumper(delegate, innerclassTypeUsageInformation);
    }
}
