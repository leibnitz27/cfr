package org.benf.cfr.reader.state;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;

import java.io.*;
import java.util.*;

public class DCCommonState {

    private final ClassCache classCache = new ClassCache(this);
    private final ClassFileSource classFileSource;
    private final Options options;

    private transient LinkedHashSet<String> couldNotLoadClasses = new LinkedHashSet<String>();

    public DCCommonState(Options options, ClassFileSource classFileSource) {
        this.options = options;
        this.classFileSource = classFileSource;
    }

    public void configureWith(ClassFile classFile) {
        classFileSource.informAnalysisRelativePathDetail(classFile.getUsePath(), classFile.getFilePath());
    }

    public Set<String> getCouldNotLoadClasses() {
        return couldNotLoadClasses;
    }

    private Map<String, ClassFile> classFileCache = MapFactory.newExceptionRetainingLazyMap(new UnaryFunction<String, ClassFile>() {
        @Override
        public ClassFile invoke(String arg) {
            return loadClassFileAtPath(arg);
        }
    });

    private ClassFile loadClassFileAtPath(final String path) {
        try {
            Pair<byte[], String> content = classFileSource.getClassFileContent(path);
            ByteData data = new BaseByteData(content.getFirst());
            ClassFile res = new ClassFile(data, content.getSecond(), this);
            return res;
        } catch (Exception e) {
            couldNotLoadClasses.add(path);
            throw new CannotLoadClassException(path, e);
        }
    }


    public List<JavaTypeInstance> explicitlyLoadJar(String path) {
        List<JavaTypeInstance> output = ListFactory.newList();
        for (String classPath : classFileSource.addJar(path)) {
            // Redundant test as we're defending against a bad implementation.
            if (classPath.toLowerCase().endsWith(".class")) {
                output.add(classCache.getRefClassFor(classPath.substring(0, classPath.length() - 6)));
            }
        }
        return output;
    }

    public ClassFile getClassFile(String path) throws CannotLoadClassException {
        return classFileCache.get(path);
    }

    public JavaRefTypeInstance getClassTypeOrNull(String path) {
        try {
            ClassFile classFile = getClassFile(path);
            return (JavaRefTypeInstance) classFile.getClassType();
        } catch (CannotLoadClassException e) {
            return null;
        }
    }

    public ClassFile getClassFile(JavaTypeInstance classInfo) throws CannotLoadClassException {
        String path = classInfo.getRawName();
        path = ClassNameUtils.convertToPath(path) + ".class";
        return getClassFile(path);
    }

    public ClassFile getClassFileMaybePath(String pathOrName) throws CannotLoadClassException {
        if (pathOrName.endsWith(".class")) {
            // Fine - we're sure it's a class file.
            return getClassFile(pathOrName);
        }
        // See if this file exists - in which case it's odd.
        File f = new File(pathOrName);
        if (f.exists()) {
            f = null;
            return getClassFile(pathOrName);
        }
        return getClassFile(ClassNameUtils.convertToPath(pathOrName) + ".class");
    }


    public ClassCache getClassCache() {
        return classCache;
    }

    public Options getOptions() {
        return options;
    }

    // No fancy file identification right now, just very very simple.
    public String detectClsJar(String path) {
        if (path.toLowerCase().endsWith(".jar")) return "jar";
        return "class";
    }
}
