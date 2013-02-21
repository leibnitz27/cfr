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
import java.util.List;
import java.util.Map;

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
    private static final String NO_STRING_SWITCH_FLAG = "nostringswitch";
    private static final String NO_ENUM_SWITCH_FLAG = "noenumswitch";
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

    public boolean isNoStringSwitch() {
        return opts.containsKey(NO_STRING_SWITCH_FLAG);
    }

    public boolean isNoEnumSwitch() {
        return opts.containsKey(NO_ENUM_SWITCH_FLAG);
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

    private static byte[] getBytesFromFile(String path) throws CannotLoadClassException {
        try {
            File file = new File(path);
            InputStream is = new FileInputStream(path);

            // Get the size of the file
            long length = file.length();

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
                throw new IOException("Could not completely read file " + file.getName());
            }

            // Close the input stream and return bytes
            is.close();
            return bytes;
        } catch (IOException e) {
            throw new CannotLoadClassException(path, e);
        }
    }

    private Map<String, ClassFile> classFileCache = MapFactory.newLazyMap(new UnaryFunction<String, ClassFile>() {
        @Override
        public ClassFile invoke(String arg) {
            byte[] content = getBytesFromFile(arg);
            ByteData data = new BaseByteData(content);
            return new ClassFile(data, CFRState.this);
        }
    });

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
            return ListFactory.newList(NO_ENUM_SWITCH_FLAG, NO_STRING_SWITCH_FLAG);
        }

        @Override
        public List<? extends Argument<?>> getArguments() {
            return ListFactory.newList(SHOWOPS);
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
