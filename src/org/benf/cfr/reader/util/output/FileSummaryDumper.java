package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;

import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 20/11/2013
 * Time: 13:06
 */
public class FileSummaryDumper implements SummaryDumper {
    private final BufferedWriter writer;

    private transient JavaTypeInstance lastControllingType = null;
    private transient Method lastMethod = null;

    public FileSummaryDumper(String dir) {
        String fileName = dir + File.separator + "summary.txt";
        try {
            File file = new File(fileName);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        } catch (FileNotFoundException e) {
            throw new Dumper.CannotCreate(e);
        }
    }

    @Override
    public void notify(String message) {
        try {
            writer.write(message + "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void notifyError(JavaTypeInstance controllingType, Method method, String error) {
        try {
            if (lastControllingType != controllingType) {
                lastControllingType = controllingType;
                lastMethod = null;
                writer.write("\n\n" + controllingType.getRawName() + "\n----------------------------\n\n");
            }
            if (method != lastMethod) {
                writer.write(method.getMethodPrototype().toString() + "\n");
                lastMethod = method;
            }
            writer.write("  " + error + "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
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
}
