package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.attributes.AttributeModule;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryModuleInfo;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryPackageInfo;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.collections.CollectionUtils;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Set;

public class ClassFileDumperModule extends AbstractClassFileDumper {

    public ClassFileDumperModule(DCCommonState dcCommonState) {
        super(dcCommonState);
    }

    @Override
    public Dumper dump(ClassFile classFile, InnerClassDumpType innerClass, Dumper d) {
        dumpTopHeader(classFile, d, false);
        dumpImports(d, classFile);
        dumpComments(classFile, d);
        dumpAnnotations(classFile, d);

        AttributeModule module = classFile.getAttributes().getByName(AttributeModule.ATTRIBUTE_NAME);
        ConstantPool cp = module.getCp();
        Set<AttributeModule.ModuleFlags> flags = module.getFlags();
        d.print(CollectionUtils.joinPostFix(flags, " "));
        d.print("module ").print(module.getModuleName()).print(" {").newln();
        d.indent(1);
        String moduleVersion = module.getModuleVersion();
        if (moduleVersion != null) {
            d.comment("version: " + moduleVersion);
            d.newln();
        }

        dumpRequires(cp, d, module.getRequires());
        dumpOpensExports(cp, d, module.getExports(), "exports");
        dumpOpensExports(cp, d, module.getOpens(), "opens");
        dumpUses(cp, d, module.getUses());
        dumpProvides(cp, d, module.getProvides());
        d.indent(-1);
        d.print("}").newln();
        return d;
    }

    private void dumpRequires(ConstantPool cp, Dumper d, List<AttributeModule.Require> l) {
        if (l.isEmpty()) {
            return;
        }
        boolean effect = false;
        for (AttributeModule.Require r : l) {
            Set<AttributeModule.ModuleContentFlags> flags = r.getFlags();
            if (flags.contains(AttributeModule.ModuleContentFlags.MANDATED)) {
                continue;
            }
            ConstantPoolEntryModuleInfo module = cp.getModuleEntry(r.getIndex());
            d.print("requires ");
            d.print(CollectionUtils.joinPostFix(flags, " "));
            d.print(module.getName().getValue());

            int versionIndex = r.getVersionIndex();
            if (versionIndex != 0) {
                d.print(" /* version: " + cp.getUTF8Entry(versionIndex).getValue() + " */");
            }

            d.endCodeln();
            effect = true;
        }
        if (effect) {
            d.newln();
        }
    }

    private void dumpOpensExports(ConstantPool cp, Dumper d, List<AttributeModule.ExportOpen> l, String prefix) {
        if (l.isEmpty()) {
            return;
        }
        boolean effect = false;
        for (AttributeModule.ExportOpen r : l) {
            Set<AttributeModule.ModuleContentFlags> flags = r.getFlags();
            if (flags.contains(AttributeModule.ModuleContentFlags.MANDATED)) {
                continue;
            }
            ConstantPoolEntryPackageInfo pck = cp.getPackageEntry(r.getIndex());
            d.keyword(CollectionUtils.joinPostFix(flags, " "));
            d.keyword(prefix).print(' ').print(pck.getPackageName());
            int[] to = r.getToIndex();
            if (to.length != 0) {
                d.print(" to ");
                boolean first = true;
                for (int t : to) {
                    first = StringUtils.comma(first, d);
                    ConstantPoolEntryModuleInfo toModule = cp.getModuleEntry(t);
                    d.print(toModule.getName().getValue());
                }
            }
            d.endCodeln();
            effect = true;
        }
        if (effect) {
            d.newln();
        }
    }

    private void dumpUses(ConstantPool cp, Dumper d, List<AttributeModule.Use> l) {
        if (l.isEmpty()) {
            return;
        }
        boolean effect = false;
        for (AttributeModule.Use u : l) {
            ConstantPoolEntryClass pck = cp.getClassEntry(u.getIndex());
            d.print("uses ").dump(pck.getTypeInstance());
            d.endCodeln();
            effect = true;
        }
        if (effect) {
            d.newln();
        }
    }

    private void dumpProvides(ConstantPool cp, Dumper d, List<AttributeModule.Provide> l) {
        if (l.isEmpty()) {
            return;
        }
        for (AttributeModule.Provide r : l) {
            ConstantPoolEntryClass pck = cp.getClassEntry(r.getIndex());
            d.print("provides ").dump(pck.getTypeInstance());
            int[] with = r.getWithIndex();
            if (with.length != 0) {
                d.print(" with ");
                boolean first = true;
                for (int t : with) {
                    first = StringUtils.comma(first, d);
                    ConstantPoolEntryClass toModule = cp.getClassEntry(t);
                    d.dump(toModule.getTypeInstance());
                }
            }
            d.endCodeln();
        }
    }


    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        // we don't collect.  We want the dumper to use the full names.
    }
}
