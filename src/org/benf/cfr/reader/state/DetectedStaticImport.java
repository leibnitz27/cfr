package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

public class DetectedStaticImport {
    final JavaTypeInstance clazz;
    final String name;

    public JavaTypeInstance getClazz() {
        return clazz;
    }

    public String getName() {
        return name;
    }

    DetectedStaticImport(JavaTypeInstance clazz, String name) {
        this.clazz = clazz;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DetectedStaticImport that = (DetectedStaticImport) o;

        if (!clazz.equals(that.clazz)) return false;
        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = clazz.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}
