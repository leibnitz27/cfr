package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.NullMapping;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static org.benf.cfr.reader.api.SinkReturns.TokenType.*;

public class TokenStreamDumper extends AbstractDumper {
    private final RecycleToken tok = new RecycleToken();
    private final Token cr = new Token(NEWLINE, "\n", (Object) null);
    private final OutputSinkFactory.Sink<SinkReturns.Token> sink;
    private final int version;
    private final JavaTypeInstance classType;
    private final MethodErrorCollector methodErrorCollector;
    private final TypeUsageInformation typeUsageInformation;
    private final Options options;
    private final IllegalIdentifierDump illegalIdentifierDump;

    // We don't want to expose internals - we are simply making a offering to allow consumers to associate tokens.
    private final Map<Object, Object> refMap = MapFactory.newLazyMap(new IdentityHashMap<Object, Object>(), new UnaryFunction<Object, Object>() {
        @Override
        public Object invoke(Object arg) {
            return new Object();
        }
    });

    private final Set<JavaTypeInstance> emitted = SetFactory.newSet();

    TokenStreamDumper(OutputSinkFactory.Sink<SinkReturns.Token> sink, int version, JavaTypeInstance classType, MethodErrorCollector methodErrorCollector, TypeUsageInformation typeUsageInformation, Options options, IllegalIdentifierDump illegalIdentifierDump, MovableDumperContext context) {
        super(context);
        this.sink = sink;
        this.version = version;
        this.classType = classType;
        this.methodErrorCollector = methodErrorCollector;
        this.typeUsageInformation = typeUsageInformation;
        this.options = options;
        this.illegalIdentifierDump = illegalIdentifierDump;
    }

    /*
     * Re-used basic token to avoid allocation.
     */
    private static class RecycleToken implements SinkReturns.Token {
        private SinkReturns.TokenType type;
        private String text;

        @Override
        public SinkReturns.TokenType getTokenType() {
            return type;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public Object getRawValue() {
            return null;
        }

        @Override
        public Set<SinkReturns.TokenTypeFlags> getFlags() {
            return Collections.emptySet();
        }

        SinkReturns.Token set(SinkReturns.TokenType type, String text) {
            this.text = text;
            this.type = type;
            return this;
        }
    }

    private static class Token implements SinkReturns.Token {

        private final SinkReturns.TokenType type;
        private final String value;
        private final Object raw;
        private final Set<SinkReturns.TokenTypeFlags> flags;

        Token(SinkReturns.TokenType type, String value, Object raw) {
            this(type, value, raw, Collections.<SinkReturns.TokenTypeFlags>emptySet());
        }

        Token(SinkReturns.TokenType type, String value, Object raw, SinkReturns.TokenTypeFlags flag) {
            this(type, value, raw, Collections.singleton(flag));
        }

        Token(SinkReturns.TokenType type, String value, SinkReturns.TokenTypeFlags flag) {
            this(type, value, null, Collections.singleton(flag));
        }

        Token(SinkReturns.TokenType type, String value, SinkReturns.TokenTypeFlags... flags) {
            this(type, value, null, SetFactory.newSet(flags));
        }

        private Token(SinkReturns.TokenType type, String value, Object raw, Set<SinkReturns.TokenTypeFlags> flags) {
            this.type = type;
            this.value = value;
            this.raw = raw;
            this.flags = flags;
        }

        @Override
        public SinkReturns.TokenType getTokenType() {
            return type;
        }

        @Override
        public String getText() {
            return value;
        }

        @Override
        public Object getRawValue() {
            return raw;
        }

        @Override
        public Set<SinkReturns.TokenTypeFlags> getFlags() {
            return flags;
        }
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public ObfuscationMapping getObfuscationMapping() {
        return NullMapping.INSTANCE;
    }

    private void sink(SinkReturns.TokenType type, String text) {
        flushPendingCR();
        sink.write(tok.set(adjustComment(type), text));
    }

    private SinkReturns.TokenType adjustComment(SinkReturns.TokenType type) {
        // TODO : It may be preferable to introduce a new 'blockcommentmember' type.
        return context.inBlockComment == BlockCommentState.Not ? type : COMMENT;
    }

    private void sink(Token token) {
        flushPendingCR();
        sink.write(token);
    }

    private void flushPendingCR() {
        if (context.pendingCR) {
            context.pendingCR = false;
            sink.write(cr);
        }
    }

    @Override
    public Dumper label(String s, boolean inline) {
        sink(new Token(LABEL, s, SinkReturns.TokenTypeFlags.DEFINES));
        return this;
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        context.pendingCR = true;
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        context.pendingCR = false;
        context.atStart = false;
        return this;
    }

    @Override
    public Dumper comment(String s) {
        sink(COMMENT, s);
        return this;
    }

    @Override
    public Dumper beginBlockComment(boolean inline) {
        if (context.inBlockComment != BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to nest block comments.");
        }
        context.inBlockComment = inline ? BlockCommentState.InLine : BlockCommentState.In;
        print("/* ");
        if (inline) newln();
        return this;
    }

    @Override
    public Dumper endBlockComment() {
        if (context.inBlockComment == BlockCommentState.Not) {
            throw new IllegalStateException("Attempt to end block comment when not in one.");
        }
        if (context.inBlockComment == BlockCommentState.In) {
            if (!context.atStart) {
                newln();
            }
            print(" */").newln();
        } else {
            print(" */ ");
        }
        context.inBlockComment = BlockCommentState.Not;
        return this;
    }

    @Override
    public Dumper keyword(String s) {
        sink(KEYWORD, s);
        return this;
    }

    @Override
    public Dumper operator(String s) {
        sink(OPERATOR, s);
        return this;
    }

    @Override
    public Dumper separator(String s) {
        sink(SEPARATOR, s);
        return this;
    }

    @Override
    public Dumper literal(String s, Object o) {
        sink(new Token(LITERAL, s, o));
        return this;
    }

    @Override
    public Dumper print(String s) {
        sink(UNCLASSIFIED, s);
        return this;
    }

    @Override
    public Dumper packageName(JavaRefTypeInstance t) {
        String s = t.getPackageName();
        if (!s.isEmpty()) {
            keyword("package ").print(s).endCodeln().newln();
        }
        return this;
    }

    @Override
    public Dumper fieldName(String name, Field field, JavaTypeInstance owner, boolean hiddenDeclaration, boolean defines) {
        if (defines) {
            sink(new Token(FIELD, name, SinkReturns.TokenTypeFlags.DEFINES));
        } else {
            sink(FIELD, name);
        }
        return this;
    }

    @Override
    public Dumper methodName(String s, MethodPrototype method, boolean special, boolean defines) {
        if (defines) {
            sink(new Token(METHOD, s, refMap.get(method), SinkReturns.TokenTypeFlags.DEFINES));
        } else {
            sink(new Token(METHOD, s, refMap.get(method)));
        }
        return this;
    }

    @Override
    public Dumper parameterName(String name, MethodPrototype method, int index, boolean defines) {
        return identifier(name, null, defines);
    }

    @Override
    public Dumper variableName(String name, NamedVariable variable, boolean defines) {
        return identifier(name, null, defines);
    }

    @Override
    public Dumper identifier(String s, Object ref, boolean defines) {
        if (defines) {
            sink(new Token(IDENTIFIER, s, refMap.get(ref), SinkReturns.TokenTypeFlags.DEFINES));
        } else {
            sink(new Token(IDENTIFIER, s, refMap.get(ref)));
        }
        return this;
    }

    @Override
    public Dumper print(char c) {
        print("" + c);
        return this;
    }

    @Override
    public Dumper newln() {
        if (context.pendingCR) sink(cr);
        context.pendingCR = true;
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        sink(UNCLASSIFIED, ";");
        context.pendingCR = true;
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    @Override
    public void indent(int diff) {
        sink(diff > 0 ? INDENT : UNINDENT, "");
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext) {
        javaTypeInstance.dumpInto(this, typeUsageInformation, typeContext);
        return this;
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            keyword("null");
        } else {
            d.dump(this);
        }
        return this;
    }

    @Override
    public void close() {
        sink(EOF, "");
    }

    @Override
    public void addSummaryError(Method method, String s) {
        methodErrorCollector.addSummaryError(method, s);
    }

    @Override
    public boolean canEmitClass(JavaTypeInstance type) {
        return emitted.add(type);
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TokenStreamDumper(sink, version, classType, methodErrorCollector, innerclassTypeUsageInformation, options, illegalIdentifierDump, context);
    }

    @Override
    public int getOutputCount() {
        return context.outputCount;
    }
}
