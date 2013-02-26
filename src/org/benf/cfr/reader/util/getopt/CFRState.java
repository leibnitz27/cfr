package org.benf.cfr.reader.util.getopt;

import com.sun.javaws.exceptions.InvalidArgumentException;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.LazyMap;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;
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

    private final String fileName;
    private final String methodName;
    private final Map<String, String> opts;
    private static final PermittedOptionProvider.Argument<Integer> SHOWOPS = new PermittedOptionProvider.Argument<Integer>(
            "showops",
            new UnaryFunction<String, Integer>() {
                @Override
                public Integer invoke(String arg) {
                    if (arg == null) return 0;
                    int x = Integer.parseInt(arg);
                    if (x < 0) throw new IllegalArgumentException("required int >= 0");
                    return x;
                }
            }
    );
    private static final UnaryFunction<String, Boolean> defaultTrueBooleanDecoder = new UnaryFunction<String, Boolean>() {
        @Override
        public Boolean invoke(String arg) {
            if (arg == null) return true;
            if (arg.toLowerCase().equals("false")) return false;
            return true;
        }
    };
    public static final PermittedOptionProvider.Argument<Boolean> ENUM_SWITCH = new PermittedOptionProvider.Argument<Boolean>(
            "decodeenumswitch", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> STRING_SWITCH = new PermittedOptionProvider.Argument<Boolean>(
            "decodestringswitch", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> ARRAY_ITERATOR = new PermittedOptionProvider.Argument<Boolean>(
            "arrayiter", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> COLLECTION_ITERATOR = new PermittedOptionProvider.Argument<Boolean>(
            "collectioniter", defaultTrueBooleanDecoder);

    public CFRState(String fileName, String methodName, Map<String, String> opts) {
        this.fileName = fileName;
        this.methodName = methodName;
        this.opts = opts;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean> argument) {
        return argument.getFn().invoke(opts.get(argument.getName()));
    }

    public int getShowOps() {
        return SHOWOPS.getFn().invoke(opts.get(SHOWOPS.getName()));
    }

    public boolean isLenient() {
        return false;
    }

    public boolean analyseMethod(String thisMethodName) {
        if (methodName == null) return true;
        return methodName.equals(thisMethodName);
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

    private Map<String, ClassFile> classFileCache = MapFactory.newExceptionRetainingLazyMap(new UnaryFunction<String, ClassFile>() {
        @Override
        public ClassFile invoke(String arg) {
            return loadClassFileAtPath(arg);
        }
    });

    private ClassFile loadClassFileAtPath(String path) {
        Map<String, String> classPathFiles = getClassPathClasses();
        String jarName = classPathFiles.get(path);
        ZipFile zipFile = null;
        try {
            InputStream is = null;
            long length = 0;
            if (jarName == null) {
                File file = new File(path);
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
                return new ClassFile(data, CFRState.this);
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

    public ClassFile getClassFile(String path) throws CannotLoadClassException {
        return classFileCache.get(path);
    }

    public ClassFile getClassFile(JavaTypeInstance classInfo) throws CannotLoadClassException {
        String path = classInfo.getRawName();
        path = ClassNameUtils.convertToPath(path) + ".class";
        return getClassFile(path);
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
        public List<? extends Argument<?>> getArguments() {
            return ListFactory.newList(SHOWOPS, ENUM_SWITCH, STRING_SWITCH, ARRAY_ITERATOR, COLLECTION_ITERATOR);
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
}
