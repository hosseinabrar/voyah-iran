import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import java.io.*;
import java.security.*;
import java.util.concurrent.TimeUnit;

/** Source-launched by the bundled Java runtime. Never accesses ADB or the vehicle. */
class FrameworkPatch {
    static final String CLASS = "com/android/server/inputmethod/InputMethodManagerService.smali";
    static final String TYPE = "Lcom/android/server/inputmethod/InputMethodManagerService;";
    static final String HOOK = "    invoke-direct {p0, v2}, " + TYPE + "->voyahIranSelectedIme(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v2\n";
    static final Pattern METHOD = Pattern.compile("(?ms)^\\.method ([^\\n]+)\\n.*?^\\.end method");
    static Path apktool;
    static String java;

    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static String text(Path p) throws IOException { return Files.readString(p).replace("\r\n", "\n").replace("\ufeff", ""); }
    static String hash(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
    static String digest(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String normalize(String value) {
        StringBuilder result = new StringBuilder();
        for (String line : value.replace("\r\n", "\n").split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.matches("\\.(line|local|param|source)\\b.*") || t.startsWith(".end local") || t.startsWith(".restart local")) continue;
            t = t.replace("const-string/jumbo ", "const-string ");
            if (t.startsWith(".field ") && t.endsWith(":Z = false")) t = t.substring(0, t.length() - " = false".length());
            result.append(t).append('\n');
        }
        return result.toString();
    }
    static String canonical(String value) {
        TreeMap<String,String> methods = new TreeMap<>();
        Matcher m = METHOD.matcher(value);
        while (m.find()) require(methods.put(m.group(1), normalize(m.group())) == null, "Duplicate method: " + m.group(1));
        return normalize(METHOD.matcher(value).replaceAll("")) + methods.toString();
    }
    static String method(String value, String name) {
        Matcher m = METHOD.matcher(value); String result = null;
        while (m.find()) if (m.group(1).contains(name + "(")) {
            require(result == null, "Ambiguous method: " + name); result = m.group();
        }
        require(result != null, "Required method missing: " + name); return result;
    }
    static void tool(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(java, "-Xmx768m", "-jar", apktool.toString()));
        command.addAll(Arrays.asList(args));
        System.out.println("APKTOOL " + String.join(" ", args));
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder captured = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = p.inputReader(StandardCharsets.UTF_8)) {
                String line; while ((line = r.readLine()) != null) {
                    System.out.println(line); synchronized(captured) { captured.append(line).append('\n'); }
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        reader.start();
        if (!p.waitFor(240, TimeUnit.SECONDS)) { p.destroyForcibly(); reader.join(5000); throw new IOException("APKTool timeout"); }
        reader.join();
        String output = captured.toString();
        // APKTool can report a worker failure while returning zero: inspect both.
        require(p.exitValue() == 0 && !output.contains("Exception in thread") && !output.contains("Could not smali") && !output.contains("AndrolibException"), "APKTool failed");
    }
    static Map<String,Path> classes(Path root) throws IOException {
        TreeMap<String,Path> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".smali")).toList()) {
                String key = root.relativize(p).toString().replace('\\','/');
                require(result.put(key,p) == null, "Duplicate class path");
            }
        }
        return result;
    }
    static Map<String,byte[]> entries(Path path) throws Exception {
        TreeMap<String,byte[]> result = new TreeMap<>(); long total = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var list = zip.entries();
            while (list.hasMoreElements()) {
                ZipEntry e = list.nextElement(); String name = e.getName();
                require(!name.startsWith("/") && !name.contains("\\") && !Arrays.asList(name.split("/")).contains("..") && !name.contains(":"), "Unsafe ZIP entry");
                require(!name.matches("(?i)META-INF/.*\\.(RSA|DSA|EC|SF)"), "Signed framework JAR is not supported");
                require(e.getSize() >= 0 && e.getSize() <= 128L*1024*1024, "Unexpected ZIP entry size");
                total += e.getSize(); require(total <= 256L*1024*1024, "Framework too large");
                byte[] bytes; try (var in = zip.getInputStream(e)) { bytes = in.readAllBytes(); }
                require(result.put(name,bytes) == null, "Duplicate ZIP entry");
            }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 4, "Usage: FrameworkPatch.java apktool.jar templates input.jar work-directory");
        apktool = Path.of(args[0]).toAbsolutePath(); Path templates = Path.of(args[1]).toAbsolutePath();
        Path input = Path.of(args[2]).toAbsolutePath(), job = Path.of(args[3]).toAbsolutePath();
        java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        require(!Files.exists(job), "Work directory must be new"); Files.createDirectories(job);
        String originalHash = hash(input); Map<String,byte[]> originalEntries = entries(input);
        Path source = job.resolve("source"); tool("d", "-r", "-o", source.toString(), input.toString());
        Map<String,Path> before = classes(source);
        List<String> targets = before.keySet().stream().filter(k -> k.endsWith("/" + CLASS)).toList();
        require(targets.size() == 1, "Primary IME class missing or duplicated");
        String key = targets.getFirst(); String folder = key.substring(0,key.indexOf('/'));
        require(folder.equals("smali") || folder.matches("smali_classes[2-9][0-9]*"), "Unknown DEX layout");
        String dex = folder.equals("smali") ? "classes.dex" : folder.substring("smali_".length()) + ".dex";
        require(originalEntries.containsKey(dex), "Target DEX missing");
        Path target = before.get(key); String code = text(target);
        String expectedMethod = text(templates.resolve("original-method.smali")).strip();
        String helper = text(templates.resolve("helper-method.smali")).strip();
        String actualMethod = method(code, "updateInputMethodsFromSettingsLocked");
        boolean already = code.contains(".method private voyahIranSelectedIme(");
        String patchedMethod = expectedMethod.replace("    :goto_4\n", "    :goto_4\n" + HOOK);
        require(!patchedMethod.equals(expectedMethod), "Invalid patch template");
        if (already) {
            require(normalize(actualMethod).equals(normalize(patchedMethod)), "Existing IME hook differs from supported patch");
            require(normalize(method(code,"voyahIranSelectedIme")).equals(normalize(helper)), "Existing helper differs from supported patch");
            Files.copy(input, job.resolve("patched.jar"));
        } else {
            require(!code.contains("voyahIranSelectedIme") && !code.contains("voyah_iran_ime_override"), "Unknown partial IME patch");
            require(normalize(actualMethod).equals(normalize(expectedMethod)), "Unsupported keyboard method; vehicle was not modified");
            for (String field : List.of("mContext:Landroid/content/Context;", "mMethodMap:Landroid/util/ArrayMap;", "mSettings:Lcom/android/server/inputmethod/InputMethodUtils$InputMethodSettings;"))
                require(code.lines().anyMatch(l -> l.startsWith(".field ") && l.endsWith(" " + field)), "Helper field missing: " + field);
            List<Path> settings = before.entrySet().stream().filter(e -> e.getKey().endsWith("/com/android/server/inputmethod/InputMethodUtils$InputMethodSettings.smali")).map(Map.Entry::getValue).toList();
            require(settings.size()==1 && text(settings.getFirst()).contains("getEnabledInputMethodListLocked()Ljava/util/ArrayList;"), "Helper dependency missing");
            String insertion = actualMethod.replace("    :goto_4\n", "    :goto_4\n" + HOOK);
            require(!insertion.equals(actualMethod), "Keyboard anchor missing");
            code = code.replace(actualMethod,insertion) + "\n" + helper + "\n";
            Files.writeString(target,code,StandardCharsets.UTF_8);
            Path built = job.resolve("assembled.jar"); tool("b", source.toString(), "-o", built.toString());
            require(Files.isRegularFile(built), "Assembly produced no JAR");
            byte[] replacement = entries(built).get(dex); require(replacement != null, "Assembly produced no target DEX");
            Path output = job.resolve("patched.jar");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                for (var e : originalEntries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey())); zip.write(e.getKey().equals(dex) ? replacement : e.getValue()); zip.closeEntry();
                }
            }
            Map<String,byte[]> afterEntries = entries(output);
            require(originalEntries.keySet().equals(afterEntries.keySet()), "ZIP entries changed");
            for (String name : originalEntries.keySet()) if (!name.equals(dex)) require(Arrays.equals(originalEntries.get(name),afterEntries.get(name)), "Unrelated ZIP entry changed: " + name);
            Path verify = job.resolve("verify"); tool("d", "-r", "-o", verify.toString(), output.toString());
            Map<String,Path> after = classes(verify); require(before.keySet().equals(after.keySet()), "Class set changed");
            for (String name : before.keySet()) require(canonical(text(before.get(name))).equals(canonical(text(after.get(name)))), "Unexpected assembled code change: " + name);
            System.out.println("VERIFIED: " + before.size() + " classes; only the approved primary IME hook was introduced; other ZIP entries identical.");
        }
        require(hash(input).equals(originalHash), "Input changed during patching");
        String patchedHash = hash(job.resolve("patched.jar"));
        Files.writeString(job.resolve("result.txt"), "original="+originalHash+"\npatched="+patchedHash+"\nalready="+already+"\n", StandardCharsets.US_ASCII);
        System.out.println("VOYAH_PATCH_VERIFIED original=" + originalHash + " patched=" + patchedHash + " already=" + already);
    }
}
