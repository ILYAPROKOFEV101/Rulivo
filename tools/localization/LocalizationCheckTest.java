import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** No test framework or external runtime is needed to test the build checker. */
public final class LocalizationCheckTest {
    private final Path root;
    private int passed;

    private LocalizationCheckTest(Path project, Path root) throws Exception {
        this.root = root;
        Path resources = project.resolve("app/src/main/res");
        try (var paths = Files.walk(resources)) {
            for (Path path : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path relative = resources.relativize(path);
                if (path.toString().endsWith(".xml") && (relative.getName(0).toString().startsWith("values")
                        || relative.toString().replace('\\', '/').equals("xml/locales_config.xml"))) {
                    Path target = root.resolve("app/src/main/res").resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
        Files.copy(project.resolve("app/build.gradle.kts"), root.resolve("app/build.gradle.kts"));
        Files.createDirectories(root.resolve("app/src/main/java"));
        Files.writeString(root.resolve("app/src/main/java/Example.kt"), "");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the project root directory");
        Path fixture = Files.createTempDirectory("rulivo-localization-test-");
        try {
            var test = new LocalizationCheckTest(Path.of(args[0]), fixture);
            test.run();
            System.out.println("OK: " + test.passed + " localization checker regression cases passed.");
        } finally {
            try (var paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(path);
                }
            }
        }
    }

    private void run() throws Exception {
        var baseline = LocalizationCheck.check(root);
        if (!baseline.errors.isEmpty() || baseline.stringCount == 0) throw new AssertionError(baseline.errors);
        passed++;

        rejected("app/src/main/res/values-ha/strings.xml",
            s -> replaceString(s, "preview_mark_point", "."), "translation has no words");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> replaceString(s, "preview_mark_point", "Preview · mark point"), "untranslated English text");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> replaceString(s, "preview_mark_point", ". Missing beginning"), "missing sentence");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> replaceString(s, "preview_mark_point", ""), "empty translation");
        rejected("app/src/main/res/values-hi/strings.xml",
            s -> s.replaceFirst("%1\\$s", "%2\\$s"), "format arguments differ");
        rejected("app/src/main/res/values-hi/strings.xml",
            s -> s.replaceAll("<string name=\"preview_mark_point\">.*?</string>", ""), "missing [preview_mark_point]");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> s.replace("</resources>", "<string name=\"extra_resource\">Extra</string></resources>"), "extra [extra_resource]");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> s.replace("</resources>", "<string name=\"preview_mark_point\">Duplicate</string></resources>"), "duplicate resource names");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> replaceString(s, "resource_locale", "wrong"), "incorrect resource_locale");
        rejected("app/src/main/res/values-ha/strings.xml",
            s -> replaceString(s, "preview_mark_point", "ZXQ"), "translation marker");
        rejected("app/src/main/res/values-en/strings.xml",
            s -> replaceString(s, "preview_mark_point", "Different English text"), "English differs from the fallback catalog");

        for (String code : new String[] {"Text(\n text = \"Untranslated\")", "contentDescription = \"Untranslated\""}) {
            rejected("app/src/main/java/Example.kt", s -> code, "hardcoded UI text");
        }
        for (String code : new String[] {"val locale = Locale(\"fr\")", "val locale = Locale.FRENCH",
                "val locale = Locale.forLanguageTag(\"en\")", "Locale.setDefault(locale)"}) {
            rejected("app/src/main/java/Example.kt", s -> code, "pinned locale");
        }
        rejected("app/src/main/java/Example.kt", s -> "val label = \"Текст\"", "hardcoded Russian text");
        rejected("app/src/main/res/xml/locales_config.xml",
            s -> s.replace("android:name=\"hi\"", "android:name=\"fr\""), "locales_config.xml does not match");
        rejected("app/build.gradle.kts", s -> s.replace("\"hi\"", "\"fr\""), "Gradle localeFilters must match");
        rejected("app/build.gradle.kts", s -> s.replace("enableSplit = false", "enableSplit = true"), "Bundle language splits must be disabled");
    }

    private void rejected(String relative, UnaryOperator<String> change, String expected) throws Exception {
        Path path = root.resolve(relative);
        String original = Files.readString(path);
        String broken = change.apply(original);
        if (broken.equals(original)) throw new AssertionError("Fixture was not changed: " + relative);
        try {
            Files.writeString(path, broken);
            var report = LocalizationCheck.check(root);
            if (report.errors.stream().noneMatch(error -> error.contains(expected))) {
                throw new AssertionError("Expected " + expected + "; got " + report.errors);
            }
            passed++;
        } finally {
            Files.writeString(path, original);
        }
    }

    private static String replaceString(String xml, String key, String text) {
        return xml.replaceAll("(<string name=\"" + key + "\">).*?(</string>)", "$1" + text + "$2");
    }
}
