package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;

import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/11/2013
 * Time: 07:24
 */
public class FileDumper extends StreamDumper {
    private final BufferedWriter writer;

    public FileDumper(String dir, JavaTypeInstance type, TypeUsageInformation typeUsageInformation) {
        super(typeUsageInformation);
        String fileName = dir + File.separator + type.getRawName().replace(".", File.separator) + ".java";
        try {
            File file = new File(fileName);
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
}
