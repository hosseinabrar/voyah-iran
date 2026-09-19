import javax.tools.*;
import java.nio.file.*;
import java.util.*;
/** Compile-only development tool; avoids the Windows javac ZipFS close regression.
 * The dedicated JVM exits immediately afterwards, closing OS handles. */
class CompileDisplay {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var manager = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8);
        List<Path> sources = new ArrayList<>();
        for (String dir : (args.length > 3 ? List.of("src", "stubs", "tests") : List.of("src", "stubs"))) try (var paths=Files.walk(Path.of(args[0],dir))) {
            sources.addAll(paths.filter(p->p.toString().endsWith(".java")).toList());
        }
        Files.createDirectories(Path.of(args[2]));
        boolean ok = compiler.getTask(null,manager,null,List.of("-source","8","-target","8","-Xlint:-options","-classpath",args[1],"-d",args[2]),null,
            manager.getJavaFileObjectsFromPaths(sources)).call();
        if (!ok) System.exit(1);
        System.out.println("Display helper compiled.");
    }
}
