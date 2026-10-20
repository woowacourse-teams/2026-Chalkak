import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Parses source only: no compilation, annotation processing, dependencies or
 * file writes.
 */
public class CheckJavaLayout {

    public static void main(String[] args) throws Exception {
        var files = new ArrayList<Path>();
        for (String argument : args) {
            Path path = Path.of(argument);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Java source path does not exist: " + path);
            }
            try (var paths = Files.walk(path)) {
                files.addAll(paths.filter(file -> file.toString().endsWith(".java")).toList());
            }
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No Java sources selected");
        }
        files = new ArrayList<>(files.stream().distinct().sorted().toList());
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK with the Java compiler is required");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        int[] violations = {0};
        try (var manager = compiler.getStandardFileManager(
                diagnostics,
                null,
                null
        )) {
            var task = (JavacTask) compiler.getTask(
                    null,
                    manager,
                    diagnostics,
                    List.of("-proc:none"),
                    null,
                    manager.getJavaFileObjectsFromPaths(files)
            );
            var parsed = task.parse();
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : parsed) {
                String source = unit.getSourceFile().getCharContent(true).toString();
                String masked = maskLiteralsAndComments(source);
                new TreeScanner<Void, Void>() {
                    void check(
                            Tree owner,
                            List<? extends Tree> items,
                            String kind
                    ) {
                        if (items.size() < 3) {
                            return;
                        }
                        int lastEnd = (int) positions.getEndPosition(unit, items.getLast());
                        // Compact record constructors expose synthetic parameters, not a source
                        // list.
                        if (lastEnd < 0 && owner instanceof MethodTree method
                                && method.getReturnType() == null) {
                            return;
                        }
                        if (lastEnd < 0) {
                            throw new IllegalStateException(
                                    "Missing source position: " + unit.getSourceFile());
                        }
                        int close = masked.indexOf(')', lastEnd);
                        if (close < 0) {
                            throw new IllegalStateException(
                                    "Missing closing parenthesis: " + unit.getSourceFile());
                        }
                        boolean valid = startsLine(source, close);
                        for (Tree item : items) {
                            valid &= startsLine(source,
                                    (int) positions.getStartPosition(unit, item));
                        }
                        if (!valid) {
                            long line = unit.getLineMap()
                                    .getLineNumber(positions.getStartPosition(unit, owner));
                            System.err.println(unit.getSourceFile().getName() + ":" + line
                                    + ": [JavaLayout] 3+ items require each item and ')' on its own line ("
                                    + kind + ")");
                            violations[0]++;
                        }
                    }

                    @Override
                    public Void visitMethod(MethodTree node, Void unused) {
                        check(
                                node,
                                node.getParameters(),
                                "declaration"
                        );
                        return super.visitMethod(node, unused);
                    }

                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                        check(
                                node,
                                node.getArguments(),
                                "call"
                        );
                        return super.visitMethodInvocation(node, unused);
                    }

                    @Override
                    public Void visitNewClass(NewClassTree node, Void unused) {
                        check(
                                node,
                                node.getArguments(),
                                "constructor/enum call"
                        );
                        return super.visitNewClass(node, unused);
                    }

                    @Override
                    public Void visitClass(ClassTree node, Void unused) {
                        if (node.getKind() == Tree.Kind.RECORD) {
                            // Records cannot declare instance fields: these are header components.
                            List<VariableTree> components = node.getMembers().stream()
                                    .filter(member -> member instanceof VariableTree)
                                    .map(member -> (VariableTree) member)
                                    .filter(member -> !member.getModifiers().getFlags()
                                            .contains(Modifier.STATIC))
                                    .toList();
                            check(
                                    node,
                                    components,
                                    "record"
                            );
                        }
                        return super.visitClass(node, unused);
                    }
                }.scan(unit, null);
            }
        }
        boolean parseError = false;
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err
                        .println(diagnostic.getSource().getName() + ":" + diagnostic.getLineNumber()
                                + ": " + diagnostic.getMessage(null));
                parseError = true;
            }
        }
        if (parseError || violations[0] > 0) {
            System.exit(1);
        }
        System.out.println("JavaLayout: " + files.size() + " files passed");
    }

    private static boolean startsLine(String source, int position) {
        if (position < 0) {
            throw new IllegalStateException("Missing source position");
        }
        int start = source.lastIndexOf('\n', Math.max(0, position - 1)) + 1;
        return source.substring(start, position).isBlank();
    }

    /**
     * Preserve offsets while excluding parentheses inside literals and comments.
     */
    private static String maskLiteralsAndComments(String source) {
        char[] masked = source.toCharArray();
        for (int index = 0; index < source.length();) {
            int start = index;
            if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                index = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                index = end < 0 ? source.length() : end + 2;
            } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
                String quote = source.startsWith("\"\"\"", index)
                        ? "\"\"\""
                        : source.substring(index, index + 1);
                index += quote.length();
                while (index < source.length()) {
                    if (source.charAt(index) == '\\') {
                        index += 2;
                    } else if (source.startsWith(quote, index)) {
                        index += quote.length();
                        break;
                    } else {
                        index++;
                    }
                }
                index = Math.min(index, source.length());
            } else {
                index++;
                continue;
            }
            for (int offset = start; offset < index; offset++) {
                if (masked[offset] != '\n' && masked[offset] != '\r') {
                    masked[offset] = ' ';
                }
            }
        }
        return new String(masked);
    }
}
