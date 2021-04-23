package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.StringUtils;

import java.util.Arrays;

public interface ClassFileRelocator {
    String correctPath(String path);

    class NopRelocator implements ClassFileRelocator {
        public static ClassFileRelocator Instance = new NopRelocator();

        @Override
        public String correctPath(String path) {
            return path;
        }
    }

    class PrefixRelocator implements ClassFileRelocator {
        private final String pathPrefix;
        private final String classRemovePrefix;

        public PrefixRelocator(String pathPrefix, String classRemovePrefix) {
            this.pathPrefix = pathPrefix;
            this.classRemovePrefix = classRemovePrefix;
        }

        @Override
        public String correctPath(String usePath) {
            if (classRemovePrefix != null && usePath.startsWith(classRemovePrefix)) {
                usePath = usePath.substring(classRemovePrefix.length());
            }
            usePath = pathPrefix + usePath;
            return usePath;
        }
    }

    class RenamingRelocator implements ClassFileRelocator {
        private final String filePath;
        private final ClassFileRelocator base;
        private final FileDets classFile;
        private final FileDets file;

        private static class FileDets {
            String pre;
            String name;
            String ext;

            public FileDets(String pre, String name, String ext) {
                this.pre = pre;
                this.name = name;
                this.ext = ext;
            }
        }

        public RenamingRelocator(ClassFileRelocator base, String classPath, String filePath, String className, String fileName) {
            this.base = base;
            this.filePath = filePath;
            this.classFile = getDets(classPath, className);
            this.file = getDets(filePath, fileName);
        }

        private FileDets getDets(String path, String name) {
            Pair<String, String> parts = stripExt(name);
            return new FileDets(path.substring(0, path.length()-name.length()) , parts.getFirst(), parts.getSecond());
        }

        static Pair<String, String> stripExt(String name) {
            int idx = name.lastIndexOf('.');
            if (idx <= 0) return Pair.make(name, "");
            return Pair.make(name.substring(0,idx), name.substring(idx));
        }

        @Override
        public String correctPath(final String path) {
            /*
             * If /x/y/foo.txt is a/b/c/Blah.class, then what should
             * attempting to load a/b/c/Blah$1.class do?
             *
             * /x/y/foo$1.txt makes most sense, but might need to make behaviour optional.
             */
            if (path.startsWith(classFile.pre) && path.endsWith(classFile.ext)) {
                String p = path.substring(classFile.pre.length(), path.length() - classFile.ext.length());
                if (p.equals(classFile.name)) {
                    return filePath;
                }
                if (p.startsWith(classFile.name) && p.charAt(classFile.name.length()) == MiscConstants.INNER_CLASS_SEP_CHAR) {
                    return file.pre + file.name + p.substring(classFile.name.length()) + file.ext;
                }
            }
            return base.correctPath(path);
        }
    }

    class Configurator {

        ClassFileRelocator getCommonRoot(String filePath, String classPath) {
            String npath = filePath.replace('\\', '/');
            String[] fileParts = npath.split("/");
            String[] classParts = classPath.split("/");
            int fpLen = fileParts.length;
            int cpLen = classParts.length;
            int min = Math.min(fileParts.length, classParts.length);
            int diffpt = 1;
            while (diffpt < min && fileParts[fpLen-diffpt-1].equals(classParts[cpLen-diffpt-1])) {
                diffpt++;
            }
            String fname = fileParts[fpLen-1];
            String cname = classParts[cpLen-1];
            fileParts = Arrays.copyOfRange(fileParts, 0, fpLen-diffpt);
            classParts = Arrays.copyOfRange(classParts, 0, cpLen-diffpt);
            String pathPrefix = fileParts.length == 0 ? "" : (StringUtils.join(fileParts, "/") + "/");
            String classRemovePrefix = classParts.length == 0 ? "" : (StringUtils.join(classParts, "/") + "/");
            if (cname.equals(fname)
                || !cname.endsWith(".class") // just paranoia, it will!
            ) {
                return new PrefixRelocator(pathPrefix, classRemovePrefix);
            }
            return new RenamingRelocator(new PrefixRelocator(pathPrefix, classRemovePrefix), classPath, filePath, cname, fname);
        }

        ClassFileRelocator configureWith(String usePath, String specPath) {
            if (usePath == null || specPath == null || specPath.equals(usePath)) return NopRelocator.Instance;

            if (usePath.endsWith(specPath)) {
                String pathPrefix = usePath.substring(0, usePath.length() - specPath.length());
                return new PrefixRelocator(pathPrefix, null);
            }
            return getCommonRoot(usePath, specPath);
        }
    }

}
