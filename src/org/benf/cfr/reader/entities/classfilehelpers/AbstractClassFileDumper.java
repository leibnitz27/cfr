package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.FormalTypeParameter;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassCache;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.functors.UnaryFunction;
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

    protected static String getFormalParametersText(ClassSignature signature) {
        List<FormalTypeParameter> formalTypeParameters = signature.getFormalTypeParameters();
        if (formalTypeParameters == null || formalTypeParameters.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        boolean first = true;
        for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
            first = CommaHelp.comma(first, sb);
            sb.append(formalTypeParameter.toString());
        }
        sb.append('>');
        return sb.toString();
    }

    public void dumpImports(Dumper d, ClassCache classCache, ClassFile classFile) {
        List<ConstantPool> poolList = classFile.getAllCps();
        List<JavaTypeInstance> classTypes = classFile.getAllClassTypes();
        Set<JavaTypeInstance> types = classCache.getImports(poolList);
        types.removeAll(classTypes);
        List<String> names = Functional.map(types, new UnaryFunction<JavaTypeInstance, String>() {
            @Override
            public String invoke(JavaTypeInstance arg) {
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


}
