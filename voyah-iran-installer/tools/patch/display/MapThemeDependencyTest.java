import java.nio.file.*;
import java.util.*;
/** Runs against a local decoded OEM framework, without rebuilding or touching the vehicle. */
class MapThemeDependencyTest {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), templates = Path.of(args[1]);
        Map<String, Path> classes = new HashMap<>();
        try (var files = Files.walk(source)) {
            files.filter(p -> p.toString().endsWith(".smali")).forEach(p -> classes.put(source.relativize(p).toString(), p));
        }
        DisplayPolicyPatch.checkDependencies(classes, templates, args[2]);
        // The previous exact helpers must remain recognized by the updater.
        try (var files = Files.list(templates.resolve("display/legacy-before-map-theme"))) {
            files.forEach(p -> classes.put(args[2] + "/com/android/server/wm/" + p.getFileName(), p));
        }
        if (!DisplayPolicyPatch.matchesHelpers(classes, templates.resolve("display/legacy-before-map-theme"), args[2]))
            throw new AssertionError("Previous helper upgrade not recognized");
        System.out.println("PASS: OEM map theme dependencies and exact previous helper recognition");
    }
}
