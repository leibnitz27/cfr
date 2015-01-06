package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;

import java.io.*;

public class FileDumper extends StreamDumper {
    private final JavaTypeInstance type;
    private final SummaryDumper summaryDumper;
    private final BufferedWriter writer;


    private static final int MAX_FILE_LEN_MINUS_EXT = 249;
    private static final int TRUNC_PREFIX_LEN = 150;
    private static int truncCount = 0;

    private File mkFilename(String dir, Pair<String, String> names, JavaTypeInstance type, SummaryDumper summaryDumper) {
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
            className = className.substring(0, TRUNC_PREFIX_LEN) + "_cfr_" + (truncCount++);
            summaryDumper.notify("Class name " + names.getSecond() + " was shortened to " + className + " due to filesystem limitations.");
        }
        return new File(dir + File.separator + packageName.replace(".", File.separator) +
                ((packageName.length() == 0) ? "" : File.separator) +
                className + ".java");
    }

    public FileDumper(String dir, JavaTypeInstance type, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, Options options, IllegalIdentifierDump illegalIdentifierDump) {
        super(typeUsageInformation, options, illegalIdentifierDump);
        this.type = type;
        this.summaryDumper = summaryDumper;
        Pair<String, String> names = ClassNameUtils.getPackageAndClassNames(type.getRawName());
        try {
            File file = mkFilename(dir, names, type, summaryDumper);
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
