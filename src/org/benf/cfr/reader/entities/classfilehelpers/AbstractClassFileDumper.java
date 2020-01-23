package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.FormalTypeParameter;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfoUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.attributes.AttributeMap;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleAnnotations;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

abstract class AbstractClassFileDumper implements ClassFileDumper {

    static String getAccessFlagsString(Set<AccessFlag> accessFlags, AccessFlag[] dumpableAccessFlags) {
        StringBuilder sb = new StringBuilder();

        for (AccessFlag accessFlag : dumpableAccessFlags) {
            if (accessFlags.contains(accessFlag)) sb.append(accessFlag).append(' ');
        }
        return sb.toString();
    }

    private final DCCommonState dcCommonState;

    AbstractClassFileDumper(DCCommonState dcCommonState) {
        this.dcCommonState = dcCommonState;

    }


    void dumpTopHeader(ClassFile classFile, Dumper d) {
        if (dcCommonState == null) return;
        Options options = dcCommonState.getOptions();
        String header = MiscConstants.CFR_HEADER_BRA +
                (options.getOption(OptionsImpl.SHOW_CFR_VERSION) ? (" " + MiscConstants.CFR_VERSION) : "") + ".";
        d.beginBlockComment(false);
        d.print(header).newln();
        if (options.getOption(OptionsImpl.DECOMPILER_COMMENTS)) {
            TypeUsageInformation typeUsageInformation = d.getTypeUsageInformation();
            List<JavaTypeInstance> couldNotLoad = ListFactory.newList();
            for (JavaTypeInstance type : typeUsageInformation.getUsedClassTypes()) {
                if (type instanceof JavaRefTypeInstance) {
                    ClassFile loadedClass = null;
                    try {
                        loadedClass = dcCommonState.getClassFile(type);
                    } catch (CannotLoadClassException ignore) {
                    }
                    if (loadedClass == null) {
                        couldNotLoad.add(type);
                    }
                }
            }
            if (!couldNotLoad.isEmpty()) {
                d.newln();
                d.print("Could not load the following classes:").newln();
                for (JavaTypeInstance type : couldNotLoad) {
                    d.print(" ").print(type.getRawName()).newln();
                }
            }
        }
        d.endBlockComment();
        // package name may be empty, in which case it's ignored by dumper.
        d.packageName(classFile.getRefClassType());
    }

    void dumpImports(Dumper d, ClassFile classFile) {
        /*
         * It's a bit irritating that we have to check obfuscations here, but we are stripping unused types,
         * and don't want to strip obfuscated names.
         */
        List<JavaTypeInstance> classTypes = d.getObfuscationMapping().get(classFile.getAllClassTypes());
        Set<JavaRefTypeInstance> types = d.getTypeUsageInformation().getShortenedClassTypes();
        //noinspection SuspiciousMethodCalls
        types.removeAll(classTypes);
        /*
         * Now - for all inner class types, remove them, but make sure the base class of the inner class is imported.
         */
        List<JavaRefTypeInstance> inners = Functional.filter(types, new Predicate<JavaRefTypeInstance>() {
            @Override
            public boolean test(JavaRefTypeInstance in) {
                return in.getInnerClassHereInfo().isInnerClass();
            }
        });
        types.removeAll(inners);
        for (JavaRefTypeInstance inner : inners) {
            types.add(InnerClassInfoUtils.getTransitiveOuterClass(inner));
        }
        /*
         * Additional pass to find types we don't want to import for other reasons (default package, etc).
         *
         * (as with others, this could be done with an iterator pass to avoid having scan-then-remove,
         * but this feels cleaner for very little cost).
         */
        types.removeAll(Functional.filter(types, new Predicate<JavaRefTypeInstance>() {
            @Override
            public boolean test(JavaRefTypeInstance in) {
                return "".equals(in.getPackageName());
            }
        }));

        Options options = dcCommonState.getOptions();
        final IllegalIdentifierDump iid = IllegalIdentifierDump.Factory.getOrNull(options);

        Collection<JavaRefTypeInstance> importTypes = types;
        if (options.getOption(OptionsImpl.HIDE_LANG_IMPORTS)) {
            importTypes = Functional.filter(importTypes, new Predicate<JavaRefTypeInstance>() {
                @Override
                public boolean test(JavaRefTypeInstance in) {
                    return !"java.lang".equals(in.getPackageName());
                }
            });
        }

        List<String> names = Functional.map(importTypes, new UnaryFunction<JavaRefTypeInstance, String>() {
            @Override
            public String invoke(JavaRefTypeInstance arg) {
                if (arg.getInnerClassHereInfo().isInnerClass()) {
                    String name = arg.getRawName(iid);
                    return name.replace(MiscConstants.INNER_CLASS_SEP_CHAR, '.');
                }
                return arg.getRawName(iid);
            }
        });

        if (names.isEmpty()) return;
        Collections.sort(names);
        for (String name : names) {
            d.keyword("import ").print(name + ";").newln();
        }
        d.newln();
    }

    void dumpMethods(ClassFile classFile, Dumper d, boolean first, boolean asClass) {
        List<Method> methods = classFile.getMethods();
        if (!methods.isEmpty()) {
            for (Method method : methods) {
                if (method.hiddenState() != Method.Visibility.Visible) {
                    continue;
                }
                if (!first) {
                    d.newln();
                }
                first = false;
                method.dump(d, asClass);
            }
        }
    }

    void dumpComments(ClassFile classFile, Dumper d) {
        DecompilerComments comments = classFile.getDecompilerComments();
        if (comments == null) return;
        comments.dump(d);
    }

    void dumpAnnotations(ClassFile classFile, Dumper d) {
        AttributeMap classFileAttributes = classFile.getAttributes();
        AttributeRuntimeVisibleAnnotations runtimeVisibleAnnotations = classFileAttributes.getByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME) ;
        AttributeRuntimeInvisibleAnnotations runtimeInvisibleAnnotations = classFileAttributes.getByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME);
        if (runtimeVisibleAnnotations != null) runtimeVisibleAnnotations.dump(d);
        if (runtimeInvisibleAnnotations != null) runtimeInvisibleAnnotations.dump(d);
    }

}
