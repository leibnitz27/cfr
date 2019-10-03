package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerCommentSource;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.*;
import java.util.List;

public class FileSummaryDumper implements SummaryDumper {
    private final BufferedWriter writer;
    private final DecompilerCommentSource additionalComments;
    private final Options options;

    private transient JavaTypeInstance lastControllingType = null;
    private transient Method lastMethod = null;

    public FileSummaryDumper(String dir, Options options, DecompilerCommentSource additional) {
        additionalComments = additional;
        this.options = options;
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
                if (method != null) {
                    writer.write(method.getMethodPrototype().toString() + "\n");
                }
                lastMethod = method;
            }
            writer.write("  " + error + "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void notifyAdditionalAtEnd() {
        try {
            List<DecompilerComment> comments = additionalComments != null ? additionalComments.getComments() : null;
            if (comments != null && !comments.isEmpty()) {
                writer.write("\n");
                for (DecompilerComment comment : comments) {
                    writer.write(comment.toString() + "\n");
                    // It's also handy to see these on console.
                    if (!options.getOption(OptionsImpl.SILENT)) {
                        System.err.println(comment.toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            notifyAdditionalAtEnd();
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
