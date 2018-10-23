package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.collections.*;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * These are the possibilities we could be hitting when we call an overloaded method.
 * We must be sure that parameter casting rewrites don't move a call from using one method to
 * using another.
 * <p/>
 * These are "vaguely" compatible - i.e. we shouldn't be comparing (int, int) with (Integer, String)
 * as an explicit cast could never call the wrong one.
 * <p/>
 * HOWEVER - we should be comparing a vararg method, as
 * <p/>
 * a,b (int int)
 * could be confused with
 * a,[]{b,c}
 */
public class OverloadMethodSet {
    private final ClassFile classFile;

    private static class MethodData {
        private final MethodPrototype methodPrototype;
        private final List<JavaTypeInstance> methodArgs;
        private final int size;

        private MethodData(MethodPrototype methodPrototype, List<JavaTypeInstance> methodArgs) {
            this.methodPrototype = methodPrototype;
            this.methodArgs = methodArgs;
            this.size = methodArgs.size();
        }

        private JavaTypeInstance getArgType(int idx, JavaTypeInstance used) {
            if (idx >= size - 1 && methodPrototype.isVarArgs()) {
                JavaTypeInstance res = methodArgs.get(size - 1);
                if (res.getNumArrayDimensions() == used.getNumArrayDimensions() + 1) {
                    return res.removeAnArrayIndirection();
                }
                return res;
            }
            if (idx >= size) {
                return null;
            }
            return methodArgs.get(idx);
        }

        public boolean isVararg(int idx) {
            return (idx >= size - 1 && methodPrototype.isVarArgs());
        }

        public boolean is(MethodData other) {
            return methodPrototype == other.methodPrototype;
        }


        @Override
        public String toString() {
            return methodPrototype.toString();
        }

        private MethodData getBoundVersion(final GenericTypeBinder genericTypeBinder) {
            List<JavaTypeInstance> rebound = Functional.map(methodArgs, new UnaryFunction<JavaTypeInstance, JavaTypeInstance>() {
                @Override
                public JavaTypeInstance invoke(JavaTypeInstance arg) {
                    if (arg instanceof JavaGenericBaseInstance) {
                        return ((JavaGenericBaseInstance) arg).getBoundInstance(genericTypeBinder);
                    } else {
                        return arg;
                    }
                }
            });

            return new MethodData(methodPrototype, rebound);
        }
    }

    private final MethodData actualPrototype;
    private final List<MethodData> allPrototypes;

    public OverloadMethodSet(ClassFile classFile, MethodPrototype actualPrototype, List<MethodPrototype> allPrototypes) {
        this.classFile = classFile;
        UnaryFunction<MethodPrototype, MethodData> mk = new UnaryFunction<MethodPrototype, MethodData>() {
            @Override
            public MethodData invoke(MethodPrototype arg) {
                return new MethodData(arg, arg.getArgs());
            }
        };
        this.actualPrototype = mk.invoke(actualPrototype);
        this.allPrototypes = Functional.map(allPrototypes, mk);
    }

    private OverloadMethodSet(ClassFile classFile, MethodData actualPrototype, List<MethodData> allPrototypes) {
        this.classFile = classFile;
        this.actualPrototype = actualPrototype;
        this.allPrototypes = allPrototypes;
    }

    public OverloadMethodSet specialiseTo(JavaGenericRefTypeInstance type) {
        final GenericTypeBinder genericTypeBinder = classFile.getGenericTypeBinder(type);
        if (genericTypeBinder == null) return null;
        UnaryFunction<MethodData, MethodData> mk = new UnaryFunction<MethodData, MethodData>() {
            @Override
            public MethodData invoke(MethodData arg) {
                return arg.getBoundVersion(genericTypeBinder);
            }
        };
        return new OverloadMethodSet(classFile, mk.invoke(actualPrototype), Functional.map(allPrototypes, mk));
    }

    public JavaTypeInstance getArgType(int idx, JavaTypeInstance used) {
        return actualPrototype.getArgType(idx, used);
    }


    public boolean callsCorrectEntireMethod(List<Expression> args, GenericTypeBinder gtb) {
        final int argCount = args.size();
        /*
         * Don't even consider any of the matches which have too many required arguments
         */

        Set<MethodData> possibleMatches = SetFactory.newSet(
                Functional.filter(allPrototypes, new Predicate<MethodData>() {
                    @Override
                    public boolean test(MethodData in) {
                        return in.methodArgs.size() <= argCount;
                    }
                }));


        Map<Integer, Set<MethodData>> weakMatches = MapFactory.<Integer, Set<MethodData>>newLazyMap(new UnaryFunction<Integer, Set<MethodData>>() {
            @Override
            public Set<MethodData> invoke(Integer arg) {
                return SetFactory.newSet();
            }
        });

        for (int x = 0, len = args.size(); x < len; ++x) {
            Expression arg = args.get(x);
            boolean isNull = Literal.NULL.equals(arg);
            JavaTypeInstance actual = arg.getInferredJavaType().getJavaTypeInstance();
            actual = actual.getDeGenerifiedType();
            Iterator<MethodData> possiter = possibleMatches.iterator();
            while (possiter.hasNext()) {
                MethodData prototype = possiter.next();
                JavaTypeInstance argType = prototype.getArgType(x, actual);
                if (argType != null) {
                    argType = argType.getDeGenerifiedType();
                    // If the argument is an undecorated literal null, it can satisfy any object.
                    // BUT - it will preferentially satisfy an exact object type to OBJECT, so
                    // just because there are two valid ones, doesn't mean we have an ambiguity.
                    if (isNull) {
                        if (argType.isObject()) {
                            if (TypeConstants.OBJECT.equals(argType)) {
                                weakMatches.get(x).add(prototype);
                            }
                            continue;
                        } else {
                            possiter.remove();
                            continue;
                        }
                    }
                    // If it was equal, it would have been satisfied previously.
                    if ((actual.implicitlyCastsTo(argType, gtb) && actual.impreciseCanCastTo(argType, gtb))) {
                        continue;
                    }
                }
                possiter.remove();
            }
        }
        if (possibleMatches.isEmpty()) return false;

        if (possibleMatches.size() > 1 && !weakMatches.isEmpty()) {
            /* Of our matches, is one strongest?
             * We're looking to find a candidate in which the argument does NOT occur in a weak context.
             */
            for (int x=0, len=args.size();x<len;++x) {
                if (weakMatches.containsKey(x)) { // containskey - it's a lazy map.
                    Set<MethodData> weakMatchedMethods = weakMatches.get(x);
                    // If ONE of possibleMatches is NOT in weakMatchedMethods, it's the winner!
                    List<MethodData> remaining = SetUtil.differenceAtakeBtoList(possibleMatches, weakMatchedMethods);
                    if (remaining.size() == 1) {
                        possibleMatches.clear();
                        possibleMatches.addAll(remaining);
                        break;
                    }
                }
            }
        }

        if (possibleMatches.size() > 1) {
            List<MethodData> remaining = ListFactory.newList(possibleMatches);
            // Otherwise - if one of them is the most specific type....
            // Doesn't work if unrelated. (see JLS 15.12.2)
            for (int x=0, len=args.size();x<len;++x) {
                JavaTypeInstance argTypeUsed = args.get(x).getInferredJavaType().getJavaTypeInstance();
                JavaTypeInstance mostDefined = null;
                int best = -1;
                for (int y = 0, len2 = remaining.size(); y < len2; ++y) {
                    JavaTypeInstance t = remaining.get(y).getArgType(x, argTypeUsed);
                    BindingSuperContainer t2bs = t.getBindingSupers();
                    if (t2bs == null) {
                        best = -1;
                        break;
                    }
                    if (mostDefined == null) {
                        mostDefined = t;
                        best = 0;
                        continue;
                    }
                    boolean ainb = t2bs.containsBase(mostDefined);
                    boolean bina = mostDefined.getBindingSupers().containsBase(t);
                    if (ainb ^ bina) {
                        if (ainb) {
                            mostDefined = t;
                            best = y;
                        } else {
                            // do nothing
                        }
                    } else {
                        best = -1;
                        break;
                    }
                }
                if (best != -1) {
                    MethodData match = remaining.get(best);
                    possibleMatches.clear();
                    possibleMatches.add(match);
                    break;
                }
            }
        }

        if (possibleMatches.size() == 1) {
            MethodData methodData = possibleMatches.iterator().next();
            return methodData.methodPrototype.equals(actualPrototype.methodPrototype);
        }
        return false;
    }

    /*
     * Find which method this argument is MOST appropriate to.
     * If multiple, return false by definition.
     *
     * So if we have
     *
     * short arg
     *
     * the real method was expecting int.
     *
     * and there are methods expecting short, int, byte
     * then we're calling the wrong method, because short is an exact match.
     *
     * but if there are now methods expecting int, long.
     *
     *
     */
    public boolean callsCorrectMethod(Expression newArg, int idx, GenericTypeBinder gtb) {
        JavaTypeInstance newArgType = newArg.getInferredJavaType().getJavaTypeInstance();

        /* First pass - find an exact match for the supplied arg - if the target method is ONE of the exact matches
         * for this arg, the we're no more wrong than we could have been... (!).
         */
        Set<MethodPrototype> exactMatches = SetFactory.newSet();
        for (MethodData prototype : allPrototypes) {
            JavaTypeInstance type = prototype.getArgType(idx, newArgType);

            if ((type != null && type.equals(newArgType))) {
                exactMatches.add(prototype.methodPrototype);
            }
        }
        // This is ok.
        if (exactMatches.contains(actualPrototype.methodPrototype)) return true;
        /*
         * Ok, we aren't a perfect match.  Find the set of arguments we COULD be cast to,
         * and sort according to closest match.
         * Iff there is ONE closest match, which is our target method, fine.
         *
         * We have to be aware of boxing here - (i.e.) Integer is a close match for int, but not perfect.
         * .. however integer is
         */
        JavaTypeInstance expectedArgType = actualPrototype.getArgType(idx, newArgType);

        if (expectedArgType instanceof RawJavaType) {
            return callsCorrectApproxRawMethod(newArg, newArgType, idx, gtb);
        } else {
            return callsCorrectApproxObjMethod(newArg, newArgType, idx, gtb);
        }
    }

    public boolean callsCorrectApproxRawMethod(Expression newArg, JavaTypeInstance actual, int idx, GenericTypeBinder gtb) {
        List<MethodData> matches = ListFactory.newList();
        for (MethodData prototype : allPrototypes) {
            JavaTypeInstance arg = prototype.getArgType(idx, actual);
            // If it was equal, it would have been satisfied previously.
            if (actual.implicitlyCastsTo(arg, null) && actual.impreciseCanCastTo(arg, gtb)) {
                matches.add(prototype);
            }
        }
        if (matches.isEmpty()) {
            // WTF?
            return false;
        }
        if (matches.size() == 1 && matches.get(0).is(actualPrototype)) {
            return true;
        }
        /*
         * Need to sort them according to how much type promotion is needed, we require our target
         * to be the first one.
         *
         * When ordering, if the actual type was an object, then we order boxed arguments before literals
         * otherwise we do it inverted.
         *
         * If succeeded,
         * to be truly accurate, this set has to be the set used to verify the type promotion for
         * subsequent arguments.
         */
        boolean boxingFirst = (!(actual instanceof RawJavaType));
        /*
         * We don't need to sort, we can just do a single run.
         */
        MethodData lowest = matches.get(0);
        JavaTypeInstance lowestType = lowest.getArgType(idx, actual);
        for (int x = 1; x < matches.size(); ++x) {
            MethodData next = matches.get(x);
            JavaTypeInstance nextType = next.getArgType(idx, actual);
            if (nextType.implicitlyCastsTo(lowestType, null)) {
                lowest = next;
                lowestType = nextType;
            }
        }

        if (lowest.is(actualPrototype)) return true;
        return false;
    }

    public boolean callsCorrectApproxObjMethod(Expression newArg, final JavaTypeInstance actual, final int idx, GenericTypeBinder gtb) {
        List<MethodData> matches = ListFactory.newList();
        boolean podMatchExists = false;
        boolean nonPodMatchExists = false;
        for (MethodData prototype : allPrototypes) {
            JavaTypeInstance arg = prototype.getArgType(idx, actual);
            // If it was equal, it would have been satisfied previously.
            if (arg != null && actual.implicitlyCastsTo(arg, null) && actual.impreciseCanCastTo(arg, gtb)) {
                if (arg instanceof RawJavaType) {
                    podMatchExists = true;
                } else {
                    nonPodMatchExists = true;
                }
                matches.add(prototype);
            }
        }
        if (matches.isEmpty()) {
            // Something's obviously very confusing.  It's probably a generic/vararg screwup.
            return false;
//            return true;
        }
        if (matches.size() == 1 && matches.get(0).is(actualPrototype)) {
            return true;
        }
        /* Special case - a literal null will cast to any ONE thing in preference to 'Object', but will
         * clash if there is more than one possibility.
         */
        Literal nullLit = new Literal(TypedLiteral.getNull());
        if (newArg.equals(nullLit) && actual == RawJavaType.NULL) {
            MethodData best = null;
            JavaTypeInstance bestType = null;
            for (MethodData match : matches) {
                JavaTypeInstance arg = match.getArgType(idx, actual);
                if (!arg.equals(TypeConstants.OBJECT)) {
                    if (best == null) {
                        best = match;
                        bestType = arg;
                    } else {
                        if (arg.implicitlyCastsTo(bestType, null)) {
                            best = match;
                            bestType = arg;
                        } else if (bestType.implicitlyCastsTo(arg, null)) {
                            // We already had the better match.
                        } else {
                            // Type collision, needs cast.
                            return false;
                        }
                    }
                }
            }
            if (best != null) {
                return (best.is(actualPrototype));
            }
        }

        /*
         * If the argument is pod, then any valid pod path beats non pod
         * i.e
         *
         * x(short(y))
         *
         * will call x(int) rather than x(Short)
         */
        boolean isPOD = actual instanceof RawJavaType;
        boolean onlyMatchPod = isPOD && podMatchExists;

        /*
         * Ok, but if the argument isn't null......
         */
        if (onlyMatchPod) matches = Functional.filter(matches, new Predicate<MethodData>() {
            @Override
            public boolean test(MethodData in) {
                return (in.getArgType(idx, actual) instanceof RawJavaType);
            }
        });
        if (!isPOD) {
            // Put object matches to the front.
            Pair<List<MethodData>, List<MethodData>> partition = Functional.partition(matches, new Predicate<MethodData>() {
                @Override
                public boolean test(MethodData in) {
                    return !(in.getArgType(idx, actual) instanceof RawJavaType);
                }
            });
            matches.clear();
            matches.addAll(partition.getFirst());
            if (!nonPodMatchExists) matches.addAll(partition.getSecond());
        }

        if (matches.isEmpty()) return false;
        MethodData lowest = matches.get(0);
        JavaTypeInstance lowestType = lowest.getArgType(idx, actual);
        for (int x = 0; x < matches.size(); ++x) {
            MethodData next = matches.get(x);
            JavaTypeInstance nextType = next.getArgType(idx, actual);
            if (nextType.implicitlyCastsTo(lowestType, null)) {
                lowest = next;
                lowestType = nextType;
            }
        }

        if (lowest.is(actualPrototype)) return true;
        return false;
    }
}
