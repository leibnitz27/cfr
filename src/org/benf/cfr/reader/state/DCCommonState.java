package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.configuration.ConfigCallback;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.BadParametersException;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/11/2013
 * Time: 17:11
 */
public class DCCommonState {

    private final ClassCache classCache = new ClassCache(this);

    private final Options options;
    /*
     * Initialisation info
     */
    private boolean initiallyConfigured;
    private boolean unexpectedDirectory = false;
    private String pathPrefix = "";
    private String classRemovePrefix = "";

    private transient LinkedHashSet<String> couldNotLoadClasses = new LinkedHashSet<String>();

    public DCCommonState(Options options) {
        this.options = options;
    }

    public void configureWith(ClassFile classFile) {
        new Configurator().configureWith(classFile);
    }

    private class Configurator implements ConfigCallback {

        private Configurator() {
        }

        /*
         * This is hideously inefficient. ;)
         */
        private void reverse(String[] in) {
            List<String> l = Arrays.asList(in);
            Collections.reverse(l);
            l.toArray(in);
        }

        private String join(String[] in, String sep) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String s : in) {
                if (first) {
                    first = false;
                } else {
                    sb.append(sep);
                }
                sb.append(s);
            }
            return sb.toString();
        }

        private void getCommonRoot(String filePath, String classPath) {
            String npath = filePath.replace('\\', '/');
            String[] fileParts = npath.split("/");
            String[] classParts = classPath.split("/");
            reverse(fileParts);
            reverse(classParts);
            int min = Math.min(fileParts.length, classParts.length);
            int diffpt = 0;
            while (diffpt < min && fileParts[diffpt].equals(classParts[diffpt])) {
                diffpt++;
            }
            fileParts = Arrays.copyOfRange(fileParts, diffpt, fileParts.length);
            classParts = Arrays.copyOfRange(classParts, diffpt, classParts.length);
            reverse(fileParts);
            reverse(classParts);
            pathPrefix = fileParts.length == 0 ? "" : (join(fileParts, "/") + "/");
            classRemovePrefix = classParts.length == 0 ? "" : (join(classParts, "/") + "/");
        }

        @Override
        public void configureWith(ClassFile partiallyConstructedClassFile) {
            String path = partiallyConstructedClassFile.getUsePath();
            JavaRefTypeInstance refTypeInstance = (JavaRefTypeInstance) partiallyConstructedClassFile.getClassType();
            String actualPath = partiallyConstructedClassFile.getFilePath();
            if (!actualPath.equals(path)) {
                unexpectedDirectory = true;
                if (path.endsWith(actualPath)) {
                    pathPrefix = path.substring(0, path.length() - actualPath.length());
                } else {
                    // We're loading from the wrong directory.  We need to rebase so that dependencies are sought
                    // in similar locations.
                    // TODO : File.separator, rather than hardcoded!
                    getCommonRoot(path, actualPath);
                }
            }
            initiallyConfigured = true;
        }
    }

    public Set<String> getCouldNotLoadClasses() {
        return couldNotLoadClasses;
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

    private ClassFile loadClassFileAtPath(final String path) {
        Map<String, String> classPathFiles = getClassPathClasses();
        String jarName = classPathFiles.get(path);
        ZipFile zipFile = null;

        try {
            InputStream is = null;
            long length = 0;

            /*
             * NB : pathPrefix will be empty the when we load the 'main' class,
             * and only set if it's not in its 'natural' location.
             */
            String usePath = path;
            if (unexpectedDirectory) {
                if (usePath.startsWith(classRemovePrefix)) {
                    usePath = usePath.substring(classRemovePrefix.length());
                }
                usePath = pathPrefix + usePath;
            }
            File file = new File(usePath);
            if (file.exists()) {
                is = new FileInputStream(file);
                length = file.length();
            } else if (jarName != null) {
                zipFile = new ZipFile(new File(jarName), ZipFile.OPEN_READ);
                ZipEntry zipEntry = zipFile.getEntry(path);
                length = zipEntry.getSize();
                is = zipFile.getInputStream(zipEntry);
            } else {
                throw new IOException("No such file");
            }


            try {
                byte[] content = getBytesFromFile(is, length);
                ByteData data = new BaseByteData(content);
                ClassFile res = new ClassFile(data, usePath, this);
                return res;
            } finally {
                if (zipFile != null) zipFile.close();
            }
        } catch (IOException e) {
            couldNotLoadClasses.add(path);
            throw new CannotLoadClassException(path, e);
        }
    }

    private boolean processClassPathFile(File file, String path, Map<String, String> classToPathMap, boolean dump) {
        try {
            ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ);
            try {
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        if (name.endsWith(".class")) {
                            if (dump) {
                                System.out.println("  " + name);
                            }
                            classToPathMap.put(name, path);
                        } else {
                            if (dump) {
                                System.out.println("  [ignoring] " + name);
                            }
                        }
                    }
                }
            } finally {
                zipFile.close();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private Map<String, String> classToPathMap;

    public List<JavaTypeInstance> explicitlyLoadJar(String path) {
        /*
         * Load the jar file explicitly, extract the names.
         */
        File file = new File(path);
        if (!file.exists()) {
            throw new ConfusedCFRException("No such jar file " + path);
        }
        Map<String, String> thisJar = MapFactory.newLinkedMap();
        if (!processClassPathFile(file, path, thisJar, false)) {
            throw new ConfusedCFRException("Failed to load jar " + path);
        }
        classToPathMap = null;
        getClassPathClasses();
        /*
         * Override everything that's in our target Jar.
         */
        List<JavaTypeInstance> output = ListFactory.newList();
        for (Map.Entry<String, String> entry : thisJar.entrySet()) {
            String classPath = entry.getKey();
            if (classPath.toLowerCase().endsWith(".class")) {
                classToPathMap.put(classPath, entry.getValue());
                if (classPath.contains("$")) continue;
                output.add(classCache.getRefClassFor(classPath.substring(0, classPath.length() - 6)));
            }
        }
        return output;
    }

    private Map<String, String> getClassPathClasses() {
        if (classToPathMap == null) {
            boolean dump = options.getOption(OptionsImpl.DUMP_CLASS_PATH);

            classToPathMap = MapFactory.newMap();
            String classPath = System.getProperty("java.class.path") + ":" + System.getProperty("sun.boot.class.path");
            if (dump) {
                System.out.println("/* ClassPath Diagnostic - searching :" + classPath);
            }
            String[] classPaths = classPath.split("" + File.pathSeparatorChar);
            for (String path : classPaths) {
                if (dump) {
                    System.out.println(" " + path);
                }
                File f = new File(path);
                if (f.exists()) {
                    if (f.isDirectory()) {
                        if (dump) {
                            System.out.println(" (Directory)");
                        }
                        // Load all the jars in that directory.
                        for (File file : f.listFiles()) {
                            processClassPathFile(file, file.getAbsolutePath(), classToPathMap, dump);
                        }
                    } else {
                        processClassPathFile(f, path, classToPathMap, dump);
                    }
                } else {
                    if (dump) {
                        System.out.println(" (Can't access)");
                    }
                }
            }
            if (dump) {
                System.out.println(" */");
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
    public boolean isJar(String path) {
        return path.toLowerCase().endsWith(".jar");
    }
}
