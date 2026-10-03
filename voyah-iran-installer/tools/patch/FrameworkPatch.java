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
        boolean keyboardAlready = code.contains(".method private voyahIranSelectedIme(");
        List<String> wmTargets = before.keySet().stream().filter(k -> k.endsWith("/com/android/server/wm/ActivityRecord.smali")).toList();
        require(wmTargets.size() == 1, "ActivityRecord missing or duplicated");
        String wmFolder = wmTargets.getFirst().substring(0,wmTargets.getFirst().indexOf('/'));
        require(wmFolder.equals("smali") || wmFolder.matches("smali_classes[2-9][0-9]*"), "Unknown WM DEX layout");
        String wmDex = wmFolder.equals("smali") ? "classes.dex" : wmFolder.substring("smali_".length()) + ".dex";
        Set<String> changedDex = new HashSet<>(List.of(dex, wmDex));
        require(originalEntries.keySet().containsAll(changedDex), "Missing display DEX");
        boolean displayAlready = DisplayPolicyPatch.check(before, templates, wmFolder);
        boolean already = keyboardAlready && displayAlready;
        String patchedMethod = expectedMethod.replace("    :goto_4\n", "    :goto_4\n" + HOOK);
        require(!patchedMethod.equals(expectedMethod), "Invalid patch template");
        if (keyboardAlready) {
            require(normalize(actualMethod).equals(normalize(patchedMethod)), "Existing IME hook differs from supported patch");
            require(normalize(method(code,"voyahIranSelectedIme")).equals(normalize(helper)), "Existing helper differs from supported patch");
        }
        if (already) {
            Files.copy(input, job.resolve("patched.jar"));
        } else {
            if (!keyboardAlready) {
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
            }
            DisplayPolicyPatch.apply(before, templates, wmFolder, source);
            Path built = job.resolve("assembled.jar"); tool("b", source.toString(), "-o", built.toString());
            require(Files.isRegularFile(built), "Assembly produced no JAR");
            Map<String,byte[]> replacements = entries(built); require(replacements.keySet().containsAll(changedDex), "Assembly produced no target DEX");
            Path output = job.resolve("patched.jar");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                for (var e : originalEntries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey())); zip.write(changedDex.contains(e.getKey()) ? replacements.get(e.getKey()) : e.getValue()); zip.closeEntry();
                }
            }
            Map<String,byte[]> afterEntries = entries(output);
            require(originalEntries.keySet().equals(afterEntries.keySet()), "ZIP entries changed");
            for (String name : originalEntries.keySet()) if (!changedDex.contains(name)) require(Arrays.equals(originalEntries.get(name),afterEntries.get(name)), "Unrelated ZIP entry changed: " + name);
            Path verify = job.resolve("verify"); tool("d", "-r", "-o", verify.toString(), output.toString());
            Map<String,Path> after = classes(verify); require(before.keySet().equals(after.keySet()), "Class set changed");
            for (String name : before.keySet()) require(canonical(text(before.get(name))).equals(canonical(text(after.get(name)))), "Unexpected assembled code change: " + name);
            System.out.println("VERIFIED: " + before.size() + " classes; approved IME and display-policy hooks verified; other ZIP entries identical.");
        }
        require(hash(input).equals(originalHash), "Input changed during patching");
        String patchedHash = hash(job.resolve("patched.jar"));
        Files.writeString(job.resolve("result.txt"), "original="+originalHash+"\npatched="+patchedHash+"\nalready="+already+"\n", StandardCharsets.US_ASCII);
        System.out.println("VOYAH_PATCH_VERIFIED original=" + originalHash + " patched=" + patchedHash + " already=" + already);
    }
}


/** Bounded, exact-template patch; rejects unknown/partial vendor implementations. */
class DisplayPolicyPatch {
    static final String WM = "com/android/server/wm/";
    static final String HELPER = "Lcom/android/server/wm/VoyahIranDisplayPolicy;";
    static final String OVERRIDE = """
.method getRequestedConfigurationOrientation()I
    .locals 1
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z
    move-result v0
    if-eqz v0, :cond_0
    const/4 v0, 0x2
    return v0
    :cond_0
    invoke-super {p0}, Lcom/android/server/wm/WindowToken;->getRequestedConfigurationOrientation()I
    move-result v0
    return v0
.end method
""";
    static final Map<String,List<String>> METHODS = Map.of(
        "ActivityRecord", List.of("getOrientation", "getRequestedOrientation", "resolveOverrideConfiguration", "layoutLetterbox"),
        "WindowState", List.of("applyGravityAndUpdateFrame"));
    static Path target(Map<String,Path> classes, String folder, String type) {
        Path p = classes.get(folder + "/" + WM + type + ".smali");
        FrameworkPatch.require(p != null, "Missing display policy class in WM DEX: " + type);
        return p;
    }
    static String patched(String name, String original) {
        if (name.equals("layoutLetterbox")) {
            // Safe-area margins belong to the vehicle bars, not to an app letterbox.
            // Destroy also unregisters its input channels, including after a display move.
            String guard = """
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z
    move-result v0
    if-eqz v0, :cond_1
    iget-object v0, p0, Lcom/android/server/wm/ActivityRecord;->mLetterbox:Lcom/android/server/wm/Letterbox;
    if-eqz v0, :cond_0
    invoke-virtual {v0}, Lcom/android/server/wm/Letterbox;->destroy()V
    const/4 v0, 0x0
    iput-object v0, p0, Lcom/android/server/wm/ActivityRecord;->mLetterbox:Lcom/android/server/wm/Letterbox;
    :cond_0
    return-void
    :cond_1
""";
            FrameworkPatch.require(original.contains("    .locals 8"), "Unexpected letterbox registers");
            for (int i = 9; i >= 0; i--) original = original.replace(":cond_" + i, ":cond_" + Integer.toHexString(i + 2));
            return original.replace("    .locals 8", "    .locals 8\n" + guard);
        }

        if (name.equals("getOrientation") || name.equals("getRequestedOrientation"))
            return original.replace("    return v0", "    invoke-static {p0, v0}, " + HELPER +
                "->orientation(Lcom/android/server/wm/ActivityRecord;I)I\n    move-result v0\n    return v0");
        if (name.equals("resolveOverrideConfiguration"))
            return original.replace("    return-void", "    invoke-static/range {p0 .. p1}, " + HELPER +
                "->configuration(Lcom/android/server/wm/ActivityRecord;Landroid/content/res/Configuration;)V\n" +
                "    invoke-static {p0}, " + HELPER + "->applies(Lcom/android/server/wm/ActivityRecord;)Z\n" +
                "    move-result v0\n    if-eqz v0, :cond_4\n" +
                // Do not let a stale portrait size-compat rectangle/scale override our new bounds.
                // Preserve mCompatDisplayInsets so native size-compat can be recomputed on disable.
                "    const/4 v0, 0x0\n    iput-object v0, p0, Lcom/android/server/wm/ActivityRecord;->mSizeCompatBounds:Landroid/graphics/Rect;\n" +
                "    const/high16 v0, 0x3f800000    # 1.0f\n    iput v0, p0, Lcom/android/server/wm/ActivityRecord;->mSizeCompatScale:F\n" +
                "    :cond_4\n    return-void");
        if (name.equals("applyGravityAndUpdateFrame")) {
            String call = "    invoke-static/range {p0 .. p3}, " + HELPER +
                "->beforeFrame(Lcom/android/server/wm/WindowState;Lcom/android/server/wm/WindowFrames;Landroid/graphics/Rect;Landroid/graphics/Rect;)V\n";
            String result = original.replaceFirst("(?m)^(    \\.locals [0-9]+\\n)", "$1" + call);
            FrameworkPatch.require(!result.equals(original), "Frame local declaration missing");
            return result.replace("    return-void", "    invoke-static/range {p0 .. p1}, " + HELPER +
                "->afterFrame(Lcom/android/server/wm/WindowState;Lcom/android/server/wm/WindowFrames;)V\n    return-void");
        }
        throw new IllegalArgumentException(name);
    }
    // Verify every OEM WM member referenced by the helper before it can enter system_server.
    static void checkDependencies(Map<String,Path> classes, Path templates, String folder) throws Exception {
        Pattern ref = Pattern.compile("L(com/android/server/wm/[^;]+);->([^\\s,]+)");
        try (var files = Files.list(templates.resolve("display/helpers"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".smali")).toList()) {
                for (String line : FrameworkPatch.text(file).lines().toList()) {
                    if (!line.strip().matches("(?:invoke-|iget-|sget-).*")) continue;
                    Matcher m = ref.matcher(line);
                    if (!m.find() || m.group(1).contains("VoyahIranDisplayPolicy")) continue;
                    String type = m.group(1), member = m.group(2);
                    boolean found = false;
                    for (int depth = 0; depth < 20; depth++) {
                        Path owner = classes.get(folder + "/" + type + ".smali");
                        if (owner == null) break;
                        String code = FrameworkPatch.text(owner);
                        for (String declaration : code.lines().toList()) {
                            if ((declaration.startsWith(".method ") || declaration.startsWith(".field ")) &&
                                    declaration.split(" = ")[0].endsWith(" " + member)) {
                                FrameworkPatch.require(!declaration.contains(" private ") && !declaration.contains(" static "),
                                    "Display dependency is not accessible: " + member);
                                found = true; break;
                            }
                        }
                        if (found) break;
                        Matcher parent = Pattern.compile("(?m)^\\.super L([^;]+);").matcher(code);
                        if (!parent.find()) break;
                        type = parent.group(1);
                    }
                    FrameworkPatch.require(found, "Unsupported display dependency: " + m.group(1) + "->" + member);
                }
            }
        }
    }
    static boolean check(Map<String,Path> classes, Path templates, String folder) throws Exception {
        checkDependencies(classes, templates, folder);
        Path activity = target(classes, folder, "ActivityRecord");
        boolean installed = FrameworkPatch.text(activity).contains(HELPER);
        for (String field : List.of("mSizeCompatBounds:Landroid/graphics/Rect;", "mSizeCompatScale:F"))
            FrameworkPatch.require(FrameworkPatch.text(activity).lines().anyMatch(l -> l.startsWith(".field ") && !l.contains(" final ") && l.endsWith(" " + field)),
                "Missing size-compat dependency: " + field);
        boolean complete = true;
        Path letterbox = target(classes, folder, "Letterbox");
        FrameworkPatch.require(FrameworkPatch.text(letterbox).contains(".method public destroy()V"), "Missing Letterbox.destroy");
        FrameworkPatch.require(FrameworkPatch.text(activity).contains(".field private mLetterbox:Lcom/android/server/wm/Letterbox;"), "Missing letterbox field");
        for (var entry : METHODS.entrySet()) {
            String code = FrameworkPatch.text(target(classes, folder, entry.getKey()));
            for (String name : entry.getValue()) {
                String original = FrameworkPatch.text(templates.resolve("display/" + entry.getKey() + "-" + name + ".original.smali")).strip();
                String actual = FrameworkPatch.method(code, name);
                boolean oldLetterbox = installed && name.equals("layoutLetterbox") && !actual.contains(HELPER);
                if (oldLetterbox) complete = false;
                String expected = installed && !oldLetterbox ? patched(name, original) : original;
                FrameworkPatch.require(FrameworkPatch.normalize(FrameworkPatch.method(code, name)).equals(FrameworkPatch.normalize(expected)),
                    "Unsupported or partial display policy: " + entry.getKey() + "." + name);
            }
        }
        String code = FrameworkPatch.text(activity);
        if (installed) FrameworkPatch.require(FrameworkPatch.normalize(FrameworkPatch.method(code, "getRequestedConfigurationOrientation")).equals(FrameworkPatch.normalize(OVERRIDE)),
            "Unknown display orientation override");
        else FrameworkPatch.require(!code.contains(".method getRequestedConfigurationOrientation("), "Vendor orientation override requires review");
        boolean current = matchesHelpers(classes, templates.resolve("display/helpers"), folder);
        if (installed) FrameworkPatch.require(current || matchesHelpers(classes, templates.resolve("display/legacy-center"), folder)
                || matchesHelpers(classes, templates.resolve("display/legacy-before-theme"), folder),
            "Display helper differs from supported current or center-only policy");
        else {
            try (var helpers = Files.list(templates.resolve("display/helpers"))) {
                for (Path h : helpers.filter(x -> x.toString().endsWith(".smali")).toList())
                    FrameworkPatch.require(!classes.containsKey(folder + "/" + WM + h.getFileName()), "Unexpected display helper already exists");
            }
        }
        return installed && current && complete;
    }
    static boolean matchesHelpers(Map<String,Path> classes, Path directory, String folder) throws Exception {
        if (!Files.isDirectory(directory)) return false;
        try (var files = Files.list(directory)) {
            var helpers = files.filter(x -> x.toString().endsWith(".smali")).toList();
            if (helpers.size() != 4) return false;
            for (Path h : helpers) {
                Path existing = classes.get(folder + "/" + WM + h.getFileName());
                if (existing == null || !FrameworkPatch.canonical(FrameworkPatch.text(existing)).equals(FrameworkPatch.canonical(FrameworkPatch.text(h)))) return false;
            }
        }
        return true;
    }
    static void apply(Map<String,Path> classes, Path templates, String folder, Path source) throws Exception {
        if (check(classes, templates, folder)) return;
        boolean upgrading = FrameworkPatch.text(target(classes, folder, "ActivityRecord")).contains(HELPER);
        for (var entry : METHODS.entrySet()) {
            Path target = target(classes, folder, entry.getKey());
            String code = FrameworkPatch.text(target);
            for (String name : entry.getValue()) {
                String original = FrameworkPatch.method(code,name);
                if (!original.contains(HELPER)) code = code.replace(original, patched(name, original));
            }
            if (!upgrading && entry.getKey().equals("ActivityRecord")) code += "\n" + OVERRIDE;
            Files.writeString(target,code,StandardCharsets.UTF_8);
        }
        try (var helpers = Files.list(templates.resolve("display/helpers"))) {
            for (Path h : helpers.filter(x -> x.toString().endsWith(".smali")).toList()) {
                String key = folder + "/" + WM + h.getFileName();
                Path dest = source.resolve(key);
                Files.copy(h,dest,StandardCopyOption.REPLACE_EXISTING);
                classes.put(key,dest);
            }
        }
        FrameworkPatch.require(check(classes,templates,folder), "Display patch verification failed");
        System.out.println("DISPLAY POLICY: displays 0 and 1, user apps + Voyah Iran, left=145 top=50; live settings switch.");
    }
}
