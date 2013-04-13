package org.benf.cfr.reader.entities.bootstrap;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodHandle;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/04/2013
 * Time: 18:58
 */
public class BootstrapMethodInfo {
    /*
     * 4.4.8 jvm spec.
     *
     * The reference_kind item of the CONSTANT_MethodHandle_info structure should have the value 6 (REF_invokeStatic)
     * or 8 (REF_newInvokeSpecial) (§5.4.3.5) or else invocation of the bootstrap method handle during call site
     * specifier resolution for an invokedynamic instruction will complete abruptly.
     */
    private final MethodHandleBehaviour methodHandleBehaviour;
    private final ConstantPoolEntryMethodRef constantPoolEntryMethodRef;
    private final ConstantPoolEntry[] bootstrapArguments;

    public BootstrapMethodInfo(ConstantPoolEntryMethodHandle methodHandle, ConstantPoolEntry[] bootstrapArguments, ConstantPool cp) {
        this.methodHandleBehaviour = methodHandle.getReferenceKind();
        if (methodHandleBehaviour != MethodHandleBehaviour.INVOKE_STATIC &&
                methodHandleBehaviour != MethodHandleBehaviour.NEW_INVOKE_SPECIAL) {
            throw new IllegalArgumentException("Expected INVOKE_STATIC / NEWINVOKE_SPECIAL, got " + methodHandleBehaviour);
        }
        this.constantPoolEntryMethodRef = cp.getMethodRefEntry(methodHandle.getReferenceIndex());
        this.bootstrapArguments = bootstrapArguments;
    }

    public ConstantPoolEntryMethodRef getConstantPoolEntryMethodRef() {
        return constantPoolEntryMethodRef;
    }
}
