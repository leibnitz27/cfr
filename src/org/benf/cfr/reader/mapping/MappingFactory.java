package org.benf.cfr.reader.mapping;

import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.state.ClassCache;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MappingFactory {
    private final ClassCache classCache;
    private final Options options;

    private MappingFactory(Options options, ClassCache classCache) {
        this.options = options;
        this.classCache = classCache;
    }

    public static ObfuscationMapping get(Options options, DCCommonState state) {
        String path = options.getOption(OptionsImpl.OBFUSCATION_PATH);
        if (path == null) {
            return NullMapping.INSTANCE;
        }
        return new MappingFactory(options, state.getClassCache()).createFromPath(path);
    }

    private Mapping createFromPath(String path) {
        FileInputStream is;
        List<ClassMapping> classMappings = ListFactory.newList();
        try {
            is = new FileInputStream(path);
            BufferedReader isr = new BufferedReader(new InputStreamReader(is));
            ClassMapping currentClassMapping = null;
            do {
                String line = isr.readLine();
                if (line == null) break;
                if (line.startsWith("#") || line.isEmpty()) continue;
                if (line.endsWith(":")) {
                    currentClassMapping = parseClassMapping(line);
                    classMappings.add(currentClassMapping);
                    continue;
                }
                if (currentClassMapping == null) {
                    throw new ConfusedCFRException("No class mapping in place - illegal mapping file?");
                }
                if (line.contains(") ")) {
                    currentClassMapping.addMethodMapping(parseMethodMapping(line));
                } else {
                    currentClassMapping.addFieldMapping(parseFieldMapping(line));
                }
            } while (true);
        } catch (FileNotFoundException e) {
            throw new ConfusedCFRException(e);
        } catch (IOException e) {
            throw new ConfusedCFRException(e);
        }

        Map<JavaRefTypeInstance, JavaRefTypeInstance> parents = MapFactory.newMap();
        Map<JavaTypeInstance, List<InnerClassAttributeInfo>> innerInfo = inferInnerClasses(classMappings, parents);
        /*
         * We will have to ALTER the inner class info of each of the children, as that won't have been detected
         * when they were created.
         */
        for (Map.Entry<JavaRefTypeInstance, JavaRefTypeInstance> pc : parents.entrySet()) {
            JavaRefTypeInstance child = pc.getKey();
            JavaRefTypeInstance parent = pc.getValue();
            child.setUnexpectedInnerClassOf(parent);
        }
        return new Mapping(options, classMappings, innerInfo);
    }

    private Map<JavaTypeInstance, List<InnerClassAttributeInfo>> inferInnerClasses(List<ClassMapping> classMappings, Map<JavaRefTypeInstance, JavaRefTypeInstance> parents) {
        Map<String, ClassMapping> byRealName = MapFactory.newMap();
        for (ClassMapping classMapping : classMappings) {
            String real = classMapping.getRealClass().getRawName();
            byRealName.put(real, classMapping);
        }
        Map<JavaTypeInstance, List<JavaTypeInstance>> children = MapFactory.newLazyMap(new UnaryFunction<JavaTypeInstance, List<JavaTypeInstance>>() {
            @Override
            public List<JavaTypeInstance> invoke(JavaTypeInstance arg) {
                return ListFactory.newList();
            }
        });
        for (ClassMapping classMapping : classMappings) {
            String real = classMapping.getRealClass().getRawName();
            int idx = real.lastIndexOf(MiscConstants.INNER_CLASS_SEP_CHAR);
            if (idx == -1) {
                continue;
            }
            String prefix = real.substring(0, idx);
            ClassMapping parent = byRealName.get(prefix);
            if (parent == null) {
                continue;
            }
            JavaRefTypeInstance parentClass = parent.getObClass();
            JavaRefTypeInstance childClass = classMapping.getObClass();
            parents.put(childClass, parentClass);
            children.get(parentClass).add(childClass);
        }
        Map<JavaTypeInstance, List<InnerClassAttributeInfo>> res = MapFactory.newMap();
        Map<JavaTypeInstance, List<InnerClassAttributeInfo>> lazyRes = MapFactory.newLazyMap(res, new UnaryFunction<JavaTypeInstance, List<InnerClassAttributeInfo>>() {
            @Override
            public List<InnerClassAttributeInfo> invoke(JavaTypeInstance arg) {
                return ListFactory.newList();
            }
        });
        for (Map.Entry<JavaTypeInstance, List<JavaTypeInstance>> entry : children.entrySet()) {
            JavaTypeInstance parent = entry.getKey();
            List<InnerClassAttributeInfo> parentIac = lazyRes.get(parent);
            for (JavaTypeInstance child : entry.getValue()) {
                InnerClassAttributeInfo iac = new InnerClassAttributeInfo(child, parent, null, Collections.<AccessFlag>emptySet());
                parentIac.add(iac);
                lazyRes.get(child).add(iac);
            }
        }
        return res;
    }

    private static final Pattern fieldPattern = Pattern.compile("^\\s*(\\d+:\\d+:)?([^ ]+)\\s+(.*) -> (.*)$");
    // java.lang.Integer color -> D
    private FieldMapping parseFieldMapping(String line) {
        Matcher m = fieldPattern.matcher(line);
        if (!m.matches()) {
            throw new ConfusedCFRException("Can't match field: " + line);
        }
        String type = m.group(2);
        String name = m.group(3);
        String rename = m.group(4);
        return new FieldMapping(name, rename, getJavaStringTypeInstance(type));
    }

    private static final Pattern methodPattern = Pattern.compile("^\\s*(\\d+:\\d+:)?([^ ]+)\\s+([^(]+)[(](.*)[)] -> (.*)$");
    // 114:114:java.lang.Integer getColor() -> e
    private MethodMapping parseMethodMapping(String line) {
        Matcher m = methodPattern.matcher(line);
        if (!m.matches()) {
            throw new ConfusedCFRException("Can't match method: " + line);
        }
        String type = m.group(2);
        String name = m.group(3);
        String args = m.group(4);
        String rename = m.group(5);
        List<JavaTypeInstance> argTypes;
        if (args.isEmpty()) {
            argTypes = Collections.emptyList();
        } else {
            argTypes = ListFactory.newList();
            for (String arg : args.split(",")) {
                arg = arg.trim();
                if (arg.isEmpty()) continue;
                argTypes.add(getJavaStringTypeInstance(arg));
            }
        }
        JavaTypeInstance result = getJavaStringTypeInstance(type);
        return new MethodMapping(name, rename, result, argTypes);
    }

    // We need to parse the class signature that we've been given.
    // Fortunately (until proven otherwise!) these are erased types, so we
    // can assume it's pod, object, or array thereof.
    private JavaTypeInstance getJavaStringTypeInstance(String type) {
        int numarray = 0;
        while (type.endsWith("[]")) {
            // yes, this is gross.
            type = type.substring(0, type.length()-2);
            numarray++;
        }
        JavaTypeInstance result = RawJavaType.getPodNamedType(type);
        if (result == null) {
            result = classCache.getRefClassFor(type);
        }
        if (numarray > 0) {
            result = new JavaArrayTypeInstance(numarray, result);
        }
        return result;
    }

    private static final Pattern classPattern = Pattern.compile("^(.+) -> (.+):$");
    // com.blah.blah2.Example -> a:
    private ClassMapping parseClassMapping(String line) {
        Matcher m = classPattern.matcher(line);
        if (!m.matches()) {
            throw new ConfusedCFRException("Can't match class: " + line);
        }
        return new ClassMapping((JavaRefTypeInstance)getJavaStringTypeInstance(m.group(1)), (JavaRefTypeInstance)getJavaStringTypeInstance(m.group(2)));
    }
}
