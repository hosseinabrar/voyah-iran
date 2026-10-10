import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.iface.value.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Complete per-class verifier/type dependencies, including code, annotations and catch types. */
public final class DexDependencies {
    static Set<String> refs;
    static void type(CharSequence value) {
        if (value == null) return;
        String t = value.toString();
        while (t.startsWith("[")) t = t.substring(1);
        if (t.startsWith("L") && t.endsWith(";")) refs.add(t);
    }
    static void reference(Reference r) {
        if (r instanceof TypeReference x) type(x.getType());
        else if (r instanceof FieldReference x) { type(x.getDefiningClass()); type(x.getType()); }
        else if (r instanceof MethodReference x) {
            type(x.getDefiningClass()); type(x.getReturnType()); x.getParameterTypes().forEach(DexDependencies::type);
        } else if (r instanceof MethodProtoReference x) {
            type(x.getReturnType()); x.getParameterTypes().forEach(DexDependencies::type);
        } else if (r instanceof MethodHandleReference x) reference(x.getMemberReference());
        else if (r instanceof CallSiteReference x) {
            reference(x.getMethodHandle()); reference(x.getMethodProto()); x.getExtraArguments().forEach(DexDependencies::value);
        } else if (!(r instanceof StringReference)) throw new IllegalArgumentException("Unknown reference: " + r.getClass());
    }
    static void value(EncodedValue v) {
        if (v instanceof TypeEncodedValue x) type(x.getValue());
        else if (v instanceof FieldEncodedValue x) reference(x.getValue());
        else if (v instanceof EnumEncodedValue x) reference(x.getValue());
        else if (v instanceof MethodEncodedValue x) reference(x.getValue());
        else if (v instanceof MethodTypeEncodedValue x) reference(x.getValue());
        else if (v instanceof MethodHandleEncodedValue x) reference(x.getValue());
        else if (v instanceof ArrayEncodedValue x) x.getValue().forEach(DexDependencies::value);
        else if (v instanceof AnnotationEncodedValue x) { type(x.getType()); x.getElements().forEach(e -> value(e.getValue())); }
    }
    static void annotations(Set<? extends Annotation> annotations) {
        annotations.forEach(a -> { type(a.getType()); a.getElements().forEach(e -> value(e.getValue())); });
    }
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        var classes = new TreeMap<String, ClassDef>();
        for (String name : container.getDexEntryNames()) {
            for (ClassDef c : container.getEntry(name).getDexFile().getClasses()) {
                if (classes.put(c.getType(), c) != null) throw new IllegalArgumentException("Duplicate class " + c.getType());
            }
        }
        try (var out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[1]), StandardCharsets.UTF_8))) {
            for (ClassDef c : classes.values()) {
                refs = new TreeSet<>(); boolean nativeCode = false;
                type(c.getSuperclass()); c.getInterfaces().forEach(DexDependencies::type); annotations(c.getAnnotations());
                for (Field f : c.getFields()) { type(f.getType()); annotations(f.getAnnotations()); value(f.getInitialValue()); }
                for (Method m : c.getMethods()) {
                    reference(m); annotations(m.getAnnotations());
                    for (MethodParameter p : m.getParameters()) annotations(p.getAnnotations());
                    nativeCode |= (m.getAccessFlags() & 0x100) != 0;
                    var code = m.getImplementation(); if (code == null) continue;
                    boolean reflective = false; var strings = new ArrayList<String>();
                    for (Instruction instruction : code.getInstructions()) {
                        if (instruction instanceof ReferenceInstruction x) {
                            Reference r = x.getReference(); reference(r);
                            if (r instanceof StringReference s) strings.add(s.getString());
                            if (r instanceof MethodReference method &&
                                ((method.getDefiningClass().equals("Ljava/lang/Class;") && method.getName().equals("forName")) ||
                                 (method.getName().equals("loadClass")))) reflective = true;
                        }
                        if (instruction instanceof DualReferenceInstruction x) reference(x.getReference2());
                    }
                    for (TryBlock<? extends ExceptionHandler> block : code.getTryBlocks())
                        for (ExceptionHandler handler : block.getExceptionHandlers()) type(handler.getExceptionType());
                    if (reflective) for (String s : strings) {
                        String descriptor = s.startsWith("L") && s.endsWith(";") ? s : "L" + s.replace('.', '/') + ";";
                        if (classes.containsKey(descriptor)) refs.add(descriptor);
                    }
                }
                refs.remove(c.getType()); refs.retainAll(classes.keySet());
                out.print(c.getType() + "\t" + (nativeCode ? "native" : "ordinary"));
                for (String ref : refs) out.print("\t" + ref);
                out.println();
            }
        }
    }
}
