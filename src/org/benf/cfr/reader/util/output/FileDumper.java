package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;

import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/11/2013
 * Time: 07:24
 */
public class FileDumper extends StreamDumper {
    private final JavaTypeInstance type;
    private final SummaryDumper summaryDumper;
    private final BufferedWriter writer;

    private static final int MAX_FILE_LEN_MINUS_EXT = 249;
    private static final int TRUNC_PREFIX_LEN = 150;

    private File mkFilename(String dir, Pair<String, String> names) {
        String packageName = names.getFirst();
        String outDir = dir;
        if (!packageName.isEmpty()) {
            outDir = outDir + File.separator + packageName.replace(".", File.separator);
        }
        String className = names.getSecond();
        if (className.length() > MAX_FILE_LEN_MINUS_EXT) {
            /*
             * Have to try to find a replacement name.
             */
            File outDirFile = new File(outDir);
            if (!outDirFile.exists() && !outDirFile.mkdirs()) {
                throw new IllegalStateException("Can't create output dir for temp file");
            }
            className = className.substring(0, TRUNC_PREFIX_LEN);
            try {
                File temp = File.createTempFile(className, ".java", outDirFile);
                summaryDumper.notify("Had to truncate class name " + names.getSecond());
                return temp;
            } catch (IOException e) {
                throw new IllegalStateException("Error creating truncated temp file");
            }
        }
        return new File(dir + File.separator + packageName.replace(".", File.separator) +
                ((packageName.length() == 0) ? "" : File.separator) +
                className + ".java");
    }

    public FileDumper(String dir, JavaTypeInstance type, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation) {
        super(typeUsageInformation);
        this.type = type;
        this.summaryDumper = summaryDumper;
        Pair<String, String> names = ClassNameUtils.getPackageAndClassNames(type.getRawName());
        try {
            File file = mkFilename(dir, names);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        } catch (FileNotFoundException e) {
            throw new CannotCreate(e);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void write(String s) {
        try {
            writer.write(s);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void addSummaryError(Method method, String s) {
        summaryDumper.notifyError(type, method, s);
    }
}
