package org.benf.cfr.reader.entities.innerclass;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.util.annotation.Nullable;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2013
 * Time: 17:56
 */
public class InnerClassAttributeInfo {
    private final
    @Nullable
    JavaTypeInstance innerClassInfo;
    private final
    @Nullable
    JavaTypeInstance outerClassInfo;
    private final
    @Nullable
    String innerName;
    private final Set<AccessFlag> accessFlags;

    public InnerClassAttributeInfo(JavaTypeInstance innerClassInfo, JavaTypeInstance outerClassInfo, String innerName, Set<AccessFlag> accessFlags) {
        this.innerClassInfo = innerClassInfo;
        this.outerClassInfo = outerClassInfo;
        this.innerName = innerName;
        this.accessFlags = accessFlags;
    }

    public JavaTypeInstance getInnerClassInfo() {
        return innerClassInfo;
    }

    public JavaTypeInstance getOuterClassInfo() {
        return outerClassInfo;
    }

    public String getInnerName() {
        return innerName;
    }

    public Set<AccessFlag> getAccessFlags() {
        return accessFlags;
    }
}
