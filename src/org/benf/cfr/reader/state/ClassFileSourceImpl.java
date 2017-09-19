package org.benf.cfr.reader.state;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ClassFileSourceImpl implements ClassFileSource {

    private Map<String, String> classToPathMap;
    // replace with BiDiMap
    private Map<String, String> classCollisionRenamerLCToReal;
    private Map<String, String> classCollisionRenamerRealToLC;
    private final Options options;
    /*
     * Initialisation info
     */
    private boolean unexpectedDirectory = false;
    private String pathPrefix = "";
    private String classRemovePrefix = "";


    public ClassFileSourceImpl(Options options) {
        this.options = options;
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

    @Override
    public String getPossiblyRenamedPath(String path) {
        if (classCollisionRenamerRealToLC == null) return path;
        String res = classCollisionRenamerRealToLC.get(path + ".class");
        if (res == null) return path;
        return res.substring(0, res.length()-6);
    }

    @Override
    public Pair<byte [], String> getClassFileContent(final String inputPath) throws IOException {
        Map<String, String> classPathFiles = getClassPathClasses();

        String jarName = classPathFiles.get(inputPath);

        // If path is an alias due to case insensitivity, restore to the correct name here, before
        // accessing zipfile.
        String path = inputPath;
        if (classCollisionRenamerLCToReal != null) {
            String actualName = classCollisionRenamerLCToReal.get(path);
            if (actualName != null) {
                path = actualName;
            }
        }

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

            byte[] content = getBytesFromFile(is, length);
            return Pair.make(content, inputPath);
        } finally {
            if (zipFile != null) zipFile.close();
        }
    }

    public Collection<String> addJar(String jarPath) {
        // Make sure classpath is scraped first, so we'll overwrite it.
        getClassPathClasses();

        File file = new File(jarPath);
        if (!file.exists()) {
            throw new ConfusedCFRException("No such jar file " + jarPath);
        }
        Map<String, String> thisJar = MapFactory.newLinkedMap();
        if (!processClassPathFile(file, jarPath, thisJar, false)) {
            throw new ConfusedCFRException("Failed to load jar " + jarPath);
        }

        Set<String> dedup = null;
        if (classCollisionRenamerLCToReal != null) {
            final Map<String, List<String>> map = Functional.groupToMapBy(thisJar.keySet(), new UnaryFunction<String, String>() {
                @Override
                public String invoke(String arg) {
                    return arg.toLowerCase();
                }
            });
            dedup = SetFactory.newSet(Functional.filter(map.keySet(), new Predicate<String>() {
                @Override
                public boolean test(String in) {
                    return map.get(in).size() > 1;
                }
            }));
        }

        List < String > output = ListFactory.newList();
        for (Map.Entry<String, String> entry : thisJar.entrySet()) {
            String classPath = entry.getKey();
            if (classPath.toLowerCase().endsWith(".class")) {
                // nb : entry.value will always be the jar here, but ....
                if (classCollisionRenamerLCToReal != null) {
                    String renamed = addDedupName(classPath, dedup, classCollisionRenamerLCToReal);
                    classCollisionRenamerRealToLC.put(classPath, renamed);
                    classPath = renamed;
                }
                classToPathMap.put(classPath, entry.getValue());
                output.add(classPath);
            }
        }
        return output;

    }

    private static String addDedupName(String potDup, Set<String> collisions, Map<String, String> data) {
        String n = potDup.toLowerCase();
        String name = n.substring(0, n.length()-6);
        int next = 0;
        if (!collisions.contains(n)) return potDup;
        String testName = name + "_" + next + ".class";
        while (data.containsKey(testName)) {
            testName = name + "_" + ++next + ".class";
        }
        data.put(testName, potDup);
        return testName;
    }

    private Map<String, String> getClassPathClasses() {
        if (classToPathMap == null) {
            boolean renameCase = (options.getOption(OptionsImpl.CASE_INSENSITIVE_FS_RENAME));

            boolean dump = options.getOption(OptionsImpl.DUMP_CLASS_PATH);

            classToPathMap = MapFactory.newMap();
            String classPath = System.getProperty("java.class.path") + File.pathSeparatorChar + System.getProperty("sun.boot.class.path");
            if (dump) {
                System.out.println("/* ClassPath Diagnostic - searching :" + classPath);
            }
            String extraClassPath = options.getOption(OptionsImpl.EXTRA_CLASS_PATH);
            if (null != extraClassPath) {
                classPath = classPath + File.pathSeparatorChar + extraClassPath;
            }
            if (renameCase) {
                classCollisionRenamerLCToReal = MapFactory.newMap();
                classCollisionRenamerRealToLC = MapFactory.newMap();
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
                        File[] files = f.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                processClassPathFile(file, file.getAbsolutePath(), classToPathMap, dump);
                            }
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

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String specPath) {
        new Configurator().configureWith(usePath, specPath);
    }

    private class Configurator {

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
            pathPrefix = fileParts.length == 0 ? "" : (StringUtils.join(fileParts, "/") + "/");
            classRemovePrefix = classParts.length == 0 ? "" : (StringUtils.join(classParts, "/") + "/");
        }

        public void configureWith(String usePath, String specPath) {
            String path = usePath;
            String actualPath = specPath;
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
        }
    }

}
