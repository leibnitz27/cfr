package org.benf.cfr.reader.state;

import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.apiunreleased.JarContent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.mapping.Mapping;
import org.benf.cfr.reader.mapping.NullMapping;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryFunction;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

public class DCCommonState {

    private final ClassCache classCache;
    private final ClassFileSource2 classFileSource;
    private final Options options;
    private final Map<String, ClassFile> classFileCache;
    private Set<JavaTypeInstance> versionCollisions;
    private transient LinkedHashSet<String> couldNotLoadClasses = new LinkedHashSet<String>();
    private final ObfuscationMapping obfuscationMapping;

    public DCCommonState(Options options, ClassFileSource2 classFileSource) {
        this.options = options;
        this.classFileSource = classFileSource;
        this.classCache = new ClassCache(this);
        this.classFileCache = MapFactory.newExceptionRetainingLazyMap(new UnaryFunction<String, ClassFile>() {
            @Override
            public ClassFile invoke(String arg) {
                return loadClassFileAtPath(arg);
            }
        });
        this.versionCollisions = SetFactory.newSet();
        this.obfuscationMapping = NullMapping.INSTANCE;
    }

    public DCCommonState(DCCommonState dcCommonState, final BinaryFunction<String, DCCommonState, ClassFile> cacheAccess) {
        this.options = dcCommonState.options;
        this.classFileSource = dcCommonState.classFileSource;
        this.classCache = new ClassCache(this);
        this.classFileCache = MapFactory.newExceptionRetainingLazyMap(new UnaryFunction<String, ClassFile>() {
            @Override
            public ClassFile invoke(String arg) {
                return cacheAccess.invoke(arg, DCCommonState.this);
            }
        });
        this.versionCollisions = dcCommonState.versionCollisions;
        this.obfuscationMapping = dcCommonState.obfuscationMapping;
    }

    // TODO : If we have any more of these, refactor to a builder!
    public DCCommonState(DCCommonState dcCommonState, ObfuscationMapping mapping) {
        this.options = dcCommonState.options;
        this.classFileSource = dcCommonState.classFileSource;
        this.classCache = new ClassCache(this);
        this.classFileCache = MapFactory.newExceptionRetainingLazyMap(new UnaryFunction<String, ClassFile>() {
            @Override
            public ClassFile invoke(String arg) {
                return loadClassFileAtPath(arg);
            }
        });
        this.versionCollisions = dcCommonState.versionCollisions;
        this.obfuscationMapping = mapping;
    }

    public void setCollisions(Set<JavaTypeInstance> versionCollisions) {
        this.versionCollisions = versionCollisions;
    }

    public Set<JavaTypeInstance> getVersionCollisions() {
        return versionCollisions;
    }

    public void configureWith(ClassFile classFile) {
        classFileSource.informAnalysisRelativePathDetail(classFile.getUsePath(), classFile.getFilePath());
    }

    String getPossiblyRenamedFileFromClassFileSource(String name) {
        return classFileSource.getPossiblyRenamedPath(name);
    }

    @SuppressWarnings("unused")
    public Set<String> getCouldNotLoadClasses() {
        return couldNotLoadClasses;
    }

    public ClassFile loadClassFileAtPath(final String path) {
        try {
            Pair<byte[], String> content = classFileSource.getClassFileContent(path);
            ByteData data = new BaseByteData(content.getFirst());
            return new ClassFile(data, content.getSecond(), this);
        } catch (Exception e) {
            couldNotLoadClasses.add(path);
            throw new CannotLoadClassException(path, e);
        }
    }

    private static boolean isMultiReleaseJar(JarContent jarContent) {
        String val = jarContent.getManifestEntries().get(MiscConstants.MULTI_RELEASE_KEY);
        if (val == null) return false;
        return Boolean.parseBoolean(val);
    }

    public TreeMap<Integer, List<JavaTypeInstance>> explicitlyLoadJar(String path) {
        JarContent jarContent = classFileSource.addJarContent(path);

        TreeMap<Integer, List<JavaTypeInstance>> baseRes = MapFactory.newTreeMap();
        Map<Integer, List<JavaTypeInstance>> res = MapFactory.newLazyMap(baseRes, new UnaryFunction<Integer, List<JavaTypeInstance>>() {
            @Override
            public List<JavaTypeInstance> invoke(Integer arg) {
                return ListFactory.newList();
            }
        });
        boolean isMultiReleaseJar = isMultiReleaseJar(jarContent);

        for (String classPath : jarContent.getClassFiles()) {
            // If the classPath is from a multi release jar, then we're going
            // to have to process it in a more unpleasant way.
            int version = 0;
            if (isMultiReleaseJar) {
                Matcher matcher = MiscConstants.MULTI_RELEASE_PATH_PATTERN.matcher(classPath);
                // It's kind of irritating that we're reprocessing each name, rather than
                // determining this in a tree structured walk through the source jar.
                if (matcher.matches()) {
                    try {
                        String ver = matcher.group(1);
                        version = Integer.parseInt(ver);
                        classPath = matcher.group(2);
                    } catch (Exception e) {
                        // This is unfortunate - someone's playing silly buggers!
                        // Ignore this file - it won't get seen by jre.
                        // (should also be impossible to get here given regex).
                        continue;
                    }
                }
            }

            // Redundant test as we're defending against a bad implementation.
            if (classPath.toLowerCase().endsWith(".class")) {
                res.get(version).add(classCache.getRefClassFor(classPath.substring(0, classPath.length() - 6)));
            }
        }
        return baseRes;
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
    public AnalysisType detectClsJar(String path) {
        String lcPath = path.toLowerCase();
        if (lcPath.endsWith(".jar") || lcPath.endsWith(".war")) return AnalysisType.JAR;
        return AnalysisType.CLASS;
    }

    public ObfuscationMapping getObfuscationMapping() {
        return obfuscationMapping;
    }
}
