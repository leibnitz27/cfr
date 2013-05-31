package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassCache;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.configuration.ConfigCallback;
import org.benf.cfr.reader.util.functors.BinaryFunction;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 16:29
 */
public class CFRState {

    private final ClassCache classCache = new ClassCache(this);

    private final String fileName;    // Ugly because we confuse parameters and state.
    private final String methodName;  // Ugly because we confuse parameters and state.
    private final Map<String, String> opts;
    private ClassFileVersion classFileVersion = new ClassFileVersion(46, 0);


    /*
     * Initialisation info
     */
    private boolean initiallyConfigured;
    private String pathPrefix = "";

    private class Configurator implements ConfigCallback {
        private final String path;

        private Configurator(String path) {
            this.path = path;
        }

        @Override
        public void configureWith(ClassFile partiallyConstructedClassFile) {
            JavaRefTypeInstance refTypeInstance = (JavaRefTypeInstance) partiallyConstructedClassFile.getClassType();
            String actualPath = partiallyConstructedClassFile.getFilePath();
            if (!actualPath.equals(path) && path.endsWith(actualPath)) {
                pathPrefix = path.substring(0, path.length() - actualPath.length());
            }
            classCache.setAnalysisType(refTypeInstance);
            initiallyConfigured = true;
        }
    }


    private static final PermittedOptionProvider.Argument<Integer, CFRState> SHOWOPS = new PermittedOptionProvider.Argument<Integer, CFRState>(
            "showops",
            new BinaryFunction<String, CFRState, Integer>() {
                @Override
                public Integer invoke(String arg, CFRState state) {
                    if (arg == null) return 0;
                    int x = Integer.parseInt(arg);
                    if (x < 0) throw new IllegalArgumentException("required int >= 0");
                    return x;
                }
            }
    );
    private static final BinaryFunction<String, CFRState, Boolean> defaultTrueBooleanDecoder = new BinaryFunction<String, CFRState, Boolean>() {
        @Override
        public Boolean invoke(String arg, CFRState ignore) {
            if (arg == null) return true;
            return Boolean.parseBoolean(arg);
        }
    };

    private static class VersionSpecificDefaulter implements BinaryFunction<String, CFRState, Boolean> {

        public ClassFileVersion versionGreaterThanOrEqual;
        public boolean resultIfGreaterThanOrEqual;

        private VersionSpecificDefaulter(ClassFileVersion versionGreaterThanOrEqual, boolean resultIfGreaterThanOrEqual) {
            this.versionGreaterThanOrEqual = versionGreaterThanOrEqual;
            this.resultIfGreaterThanOrEqual = resultIfGreaterThanOrEqual;
        }

        @Override
        public Boolean invoke(String arg, CFRState state) {
            if (arg != null) return Boolean.parseBoolean(arg);
            if (state == null) throw new IllegalStateException(); // ho ho ho.
            return (state.classFileVersion.equalOrLater(versionGreaterThanOrEqual)) ? resultIfGreaterThanOrEqual : !resultIfGreaterThanOrEqual;
        }
    }

    public static final PermittedOptionProvider.Argument<Boolean, CFRState> ENUM_SWITCH = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "decodeenumswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> ENUM_SUGAR = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "sugarenums", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> STRING_SWITCH = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "decodestringswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, true));
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> ARRAY_ITERATOR = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "arrayiter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> COLLECTION_ITERATOR = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "collectioniter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> REWRITE_LAMBDAS = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "decodelambdas", new VersionSpecificDefaulter(ClassFileVersion.JAVA_8, true));
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> DECOMPILE_INNER_CLASSES = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "innerclasses", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> REMOVE_BOILERPLATE = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "removeboilerplate", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> REMOVE_INNER_CLASS_SYNTHETICS = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "removeinnerclasssynthetics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> HIDE_BRIDGE_METHODS = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "hidebridgemethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> LIFT_CONSTRUCTOR_INIT = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "liftconstructorinit", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> REMOVE_DEAD_METHODS = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "removedeadmethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> REMOVE_BAD_GENERICS = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "removebadgenerics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, CFRState> SUGAR_ASSERTS = new PermittedOptionProvider.Argument<Boolean, CFRState>(
            "sugarasserts", defaultTrueBooleanDecoder);

    public CFRState(String fileName, String methodName, Map<String, String> opts) {
        this.fileName = fileName;
        this.methodName = methodName;
        this.opts = opts;
    }

    public void setClassFileVersion(ClassFileVersion classFileVersion) {
        this.classFileVersion = classFileVersion;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, CFRState> argument) {
        return argument.getFn().invoke(opts.get(argument.getName()), this);
    }

    public int getShowOps() {
        return SHOWOPS.getFn().invoke(opts.get(SHOWOPS.getName()), this);
    }

    public boolean isLenient() {
        return false;
    }

    public boolean hideBridgeMethods() {
        return getBooleanOpt(HIDE_BRIDGE_METHODS);
    }

    public boolean analyseMethod(String thisMethodName) {
        if (methodName == null) return true;
        return methodName.equals(thisMethodName);
    }

    public boolean analyseInnerClasses() {
        if (methodName == null) return getBooleanOpt(DECOMPILE_INNER_CLASSES);
        return false;
    }

    public boolean removeBoilerplate() {
        return getBooleanOpt(REMOVE_BOILERPLATE);
    }

    public boolean removeInnerClassSynthetics() {
        return getBooleanOpt(REMOVE_INNER_CLASS_SYNTHETICS);
    }

    public boolean rewriteLambdas() {
        return getBooleanOpt(REWRITE_LAMBDAS);
    }

    private byte[] getBytesFromFile(InputStream is, long length) throws IOException {
        // You cannot create an array using a long type.
        // It needs to be an int type.
        // Before converting to an int type, check
        // to ensure that file is not larger than Integer.MAX_VALUE.
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file");
        }

        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    private Map<Pair<String, Boolean>, ClassFile> classFileCache = MapFactory.newExceptionRetainingLazyMap(new UnaryFunction<Pair<String, Boolean>, ClassFile>() {
        @Override
        public ClassFile invoke(Pair<String, Boolean> arg) {
            return loadClassFileAtPath(arg.getFirst(), arg.getSecond());
        }
    });

    private ClassFile loadClassFileAtPath(String path, boolean withInnerClasses) {
        Map<String, String> classPathFiles = getClassPathClasses();
        String jarName = classPathFiles.get(path);
        ZipFile zipFile = null;

        ConfigCallback configCallback = null;
        if (!initiallyConfigured) {
            configCallback = new Configurator(path);
        }

        try {
            InputStream is = null;
            long length = 0;
            if (jarName == null) {
                /*
                 * NB : pathPrefix will be empty the when we load the 'main' class,
                 * and only set if it's not in its 'natural' location.
                 */
                File file = new File(pathPrefix + path);
                is = new FileInputStream(file);
                length = file.length();
            } else {
                zipFile = new ZipFile(new File(jarName), ZipFile.OPEN_READ);
                ZipEntry zipEntry = zipFile.getEntry(path);
                length = zipEntry.getSize();
                is = zipFile.getInputStream(zipEntry);
            }

            try {
                byte[] content = getBytesFromFile(is, length);
                ByteData data = new BaseByteData(content);
                ClassFile res = new ClassFile(data, CFRState.this, withInnerClasses, configCallback);
                return res;
            } finally {
                if (zipFile != null) zipFile.close();
            }
        } catch (IOException e) {
            System.err.println("** Unable to load " + path);
            throw new CannotLoadClassException(path, e);
        }
    }

    private void processClassPathFile(File file, String path, Map<String, String> classToPathMap) {
        try {
//            System.err.println("Processclasspathfile " + path);
            ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ);
            try {
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        if (name.endsWith(".class")) {
                            classToPathMap.put(name, path);
                        }
                    }
                }
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
        }
    }

    private Map<String, String> classToPathMap;

    private Map<String, String> getClassPathClasses() {
//        System.err.println("getClassPathClasses");
        if (classToPathMap == null) {
            classToPathMap = MapFactory.newMap();
            String classPath = System.getProperty("java.class.path") + ":" + System.getProperty("sun.boot.class.path");
//            System.err.println("ClassPath:" + classPath);
            String[] classPaths = classPath.split(":");
            for (String path : classPaths) {
                File f = new File(path);
                if (f.exists()) {
                    if (f.isDirectory()) {
                        // Ignore for now.
                    } else {
                        processClassPathFile(f, path, classToPathMap);
                    }
                }
            }
        }
        return classToPathMap;
    }

    public ClassFile getClassFile(String path, boolean needInnerClasses) throws CannotLoadClassException {
        return classFileCache.get(new Pair<String, Boolean>(path, needInnerClasses));
    }

    public ClassFile getClassFile(JavaTypeInstance classInfo, boolean needInnerClasses) throws CannotLoadClassException {
        String path = classInfo.getRawName();
        path = ClassNameUtils.convertToPath(path) + ".class";
        return getClassFile(path, needInnerClasses);
    }

    public static GetOptSinkFactory<CFRState> getFactory() {
        return new CFRFactory();
    }

    private static class CFRFactory implements GetOptSinkFactory<CFRState> {
        @Override
        public List<String> getFlags() {
            return ListFactory.newList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<? extends Argument<?, ?>> getArguments() {
            return ListFactory.newList(SHOWOPS, ENUM_SWITCH, ENUM_SUGAR, STRING_SWITCH, ARRAY_ITERATOR,
                    COLLECTION_ITERATOR, DECOMPILE_INNER_CLASSES, REMOVE_BOILERPLATE,
                    REMOVE_INNER_CLASS_SYNTHETICS, REWRITE_LAMBDAS, HIDE_BRIDGE_METHODS, LIFT_CONSTRUCTOR_INIT,
                    REMOVE_DEAD_METHODS, REMOVE_BAD_GENERICS, SUGAR_ASSERTS);
        }

        @Override
        public CFRState create(List<String> args, Map<String, String> opts) {
            String fname;
            String methodName = null;
            switch (args.size()) {
                case 0:
                    throw new BadParametersException("Insufficient parameters", this);
                case 1:
                    fname = args.get(0);
                    break;
                case 2:
                    fname = args.get(0);
                    methodName = args.get(1);
                    break;
                default:
                    throw new BadParametersException("Too many unqualified parameters", this);
            }
            return new CFRState(fname, methodName, opts);
        }
    }


    public ClassCache getClassCache() {
        return classCache;
    }
}
