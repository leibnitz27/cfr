package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.FormalTypeParameter;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleAnnotations;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 05:56
 */
public abstract class AbstractClassFileDumper implements ClassFileDumper {

    protected static String getAccessFlagsString(Set<AccessFlag> accessFlags, AccessFlag[] dumpableAccessFlags) {
        StringBuilder sb = new StringBuilder();

        for (AccessFlag accessFlag : dumpableAccessFlags) {
            if (accessFlags.contains(accessFlag)) sb.append(accessFlag).append(' ');
        }
        return sb.toString();
    }

    private final DCCommonState dcCommonState;

    public AbstractClassFileDumper(DCCommonState dcCommonState) {
        this.dcCommonState = dcCommonState;
    }

    protected void dumpTopHeader(ClassFile classFile, Dumper d) {
        if (dcCommonState == null) return;
        Options options = dcCommonState.getOptions();
        String header = MiscConstants.CFR_HEADER_BRA +
                (options.getBooleanOpt(OptionsImpl.SHOW_CFR_VERSION) ? (" " + MiscConstants.CFR_VERSION) : "") + ".";
        d.print("/*").newln();
        d.print(" * ").print(header).newln();
        if (options.getBooleanOpt(OptionsImpl.DECOMPILER_COMMENTS)) {
            TypeUsageInformation typeUsageInformation = d.getTypeUsageInformation();
            List<JavaTypeInstance> couldNotLoad = ListFactory.newList();
            for (JavaTypeInstance type : typeUsageInformation.getUsedClassTypes()) {
                if (type instanceof JavaRefTypeInstance) {
                    ClassFile loadedClass = null;
                    try {
                        loadedClass = dcCommonState.getClassFile(type);
                    } catch (CannotLoadClassException e) {
                    }
                    if (loadedClass == null) {
                        couldNotLoad.add(type);
                    }
                }
            }
            if (!couldNotLoad.isEmpty()) {
                d.print(" * ").newln();
                d.print(" * Could not load the following classes:").newln();
                for (JavaTypeInstance type : couldNotLoad) {
                    d.print(" *  ").print(type.getRawName()).newln();
                }
            }
        }
        d.print(" */").newln();
    }

    protected static void getFormalParametersText(ClassSignature signature, Dumper d) {
        List<FormalTypeParameter> formalTypeParameters = signature.getFormalTypeParameters();
        if (formalTypeParameters == null || formalTypeParameters.isEmpty()) return;
        d.print('<');
        boolean first = true;
        for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
            first = CommaHelp.comma(first, d);
            d.dump(formalTypeParameter);
        }
        d.print('>');
    }

    public void dumpImports(Dumper d, ClassFile classFile) {
        List<JavaTypeInstance> classTypes = classFile.getAllClassTypes();
        Set<JavaRefTypeInstance> types = d.getTypeUsageInformation().getUsedClassTypes();
        types.removeAll(classTypes);
        List<String> names = Functional.map(types, new UnaryFunction<JavaRefTypeInstance, String>() {
            @Override
            public String invoke(JavaRefTypeInstance arg) {
                String name = arg.getRawName();
                return name.replace('$', '.');
            }
        });

        if (names.isEmpty()) return;
        Collections.sort(names);
        for (String name : names) {

            d.print("import " + name + ";\n");
        }
        d.print("\n");
    }

    protected void dumpAnnotations(ClassFile classFile, Dumper d) {
        AttributeRuntimeVisibleAnnotations runtimeVisibleAnnotations = classFile.getAttributeByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME);
        AttributeRuntimeInvisibleAnnotations runtimeInvisibleAnnotations = classFile.getAttributeByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME);
        if (runtimeVisibleAnnotations != null) runtimeVisibleAnnotations.dump(d);
        if (runtimeInvisibleAnnotations != null) runtimeInvisibleAnnotations.dump(d);
    }

}
