package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FileDumper extends StreamDumper {
    private final String dir;
    private final boolean clobber;
    private final JavaTypeInstance type;
    private final SummaryDumper summaryDumper;
    private final String path;
    private final BufferedWriter writer;
    private final AtomicInteger truncCount;

    private static final int MAX_FILE_LEN_MINUS_EXT = 249;
    private static final int TRUNC_PREFIX_LEN = 150;

    private String mkFilename(String dir, Pair<String, String> names, SummaryDumper summaryDumper) {
        String packageName = names.getFirst();
        String className = names.getSecond();
        if (className.length() > MAX_FILE_LEN_MINUS_EXT) {
            /*
             * Have to try to find a replacement name.
             */
            className = className.substring(0, TRUNC_PREFIX_LEN) + "_cfr_" + truncCount.getAndIncrement();
            summaryDumper.notify("Class name " + names.getSecond() + " was shortened to " + className + " due to filesystem limitations.");
        }

        return dir + File.separator + packageName.replace(".", File.separator) +
                ((packageName.length() == 0) ? "" : File.separator) +
                className + ".java";
    }

    FileDumper(String dir, boolean clobber, JavaTypeInstance type, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, Options options, AtomicInteger truncCount, IllegalIdentifierDump illegalIdentifierDump) {
        super(typeUsageInformation, options, illegalIdentifierDump, new MovableDumperContext());
        this.truncCount = truncCount;
        this.dir = dir;
        this.clobber = clobber;
        this.type = type;
        this.summaryDumper = summaryDumper;
        String fileName = mkFilename(dir, ClassNameUtils.getPackageAndClassNames(type), summaryDumper);
        try {
            File file = new File(fileName);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }
            if (file.exists() && !clobber) {
                throw new CannotCreate("File already exists, and option '" + OptionsImpl.CLOBBER_FILES.getName() + "' not set");
            }
            path = fileName;
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

    String getFileName() {
        return path;
    }

    @Override
    public void addSummaryError(Method method, String s) {
        summaryDumper.notifyError(type, method, s);
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TypeOverridingDumper(this, innerclassTypeUsageInformation);
    }

    @Override
    public BufferedOutputStream getAdditionalOutputStream(String description) {
        String fileName = mkFilename(dir, ClassNameUtils.getPackageAndClassNames(type), summaryDumper);
        fileName = fileName + "." + description;
        try {
            File file = new File(fileName);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }
            if (file.exists() && !clobber) {
                throw new CannotCreate("File already exists, and option '" + OptionsImpl.CLOBBER_FILES.getName() + "' not set");
            }
            return new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new CannotCreate(e);
        }

    }
}
