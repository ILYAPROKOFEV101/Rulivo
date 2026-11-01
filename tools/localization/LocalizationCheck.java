import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Build-time resource checks using only the JDK already running Gradle. */
public final class LocalizationCheck {
    private static final Map<String, String> LANGUAGES = Map.of(
        "values-en", "en", "values-in", "id", "values-ur", "ur", "values-ha", "ha", "values-hi", "hi");
    // These words are valid Indonesian, rather than missing translations.
    private static final Set<String> SHARED_INDONESIAN_WORDS = Set.of("source_manual", "diameter_143", "diameter_m");
    private static final Pattern ARGUMENT = Pattern.compile("%\\d+\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]");
    private static final Pattern RUSSIAN = Pattern.compile("[А-Яа-яЁё]");
    private static final Pattern MARKER = Pattern.compile("[А-Яа-яЁё]|ZXQ|\\[0\\d{3}\\]");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]{3}");
    private static final Pattern UI_LITERAL = Pattern.compile(
        "(?:\\bText\\s*\\(\\s*(?:text\\s*=\\s*)?|\\bcontentDescription\\s*=\\s*)\"([^\"\\n]*)\"");
    private static final Pattern PINNED_LOCALE = Pattern.compile(
        "Locale\\.[A-Z_]{2,}|Locale(?:\\.forLanguageTag)?\\(\\s*\"|Locale\\.setDefault\\(");

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the project root directory");
        Report report = check(Path.of(args[0]));
        if (!report.errors.isEmpty()) {
            report.errors.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("OK: " + report.stringCount + " strings in each of " + LANGUAGES.size()
            + " languages; placeholders and UI text checks passed.");
    }

    static Report check(Path root) throws Exception {
        List<String> errors = new ArrayList<>();
        Path res = root.resolve("app/src/main/res");
        Map<String, Element> base = read(res.resolve("values"), errors);
        Set<String> translatable = base.entrySet().stream()
            .filter(entry -> !"false".equals(entry.getValue().getAttribute("translatable")))
            .map(Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new));
        if (translatable.isEmpty()) errors.add("values: no translatable strings found");
        for (var catalog : new TreeMap<>(LANGUAGES).entrySet()) {
            String directory = catalog.getKey();
            String language = catalog.getValue();
            Map<String, Element> translated = read(res.resolve(directory), errors);
            if (!translated.keySet().equals(translatable)) {
                errors.add(directory + ": missing " + difference(translatable, translated.keySet())
                    + ", extra " + difference(translated.keySet(), translatable));
            }
            Element resourceLocale = translated.get("resource_locale");
            if (resourceLocale == null || !language.equals(resourceLocale.getTextContent())) {
                errors.add(directory + ": incorrect resource_locale");
            }
            for (String key : translatable) {
                if (!translated.containsKey(key)) continue;
                String value = translated.get(key).getTextContent();
                String original = base.get(key).getTextContent();
                String location = directory + "/" + key + ": ";
                if (!placeholders(value).equals(placeholders(original))) errors.add(location + "format arguments differ");
                if (MARKER.matcher(value).find()) errors.add(location + "untranslated text or translation marker");
                if (unquote(value).isEmpty()) errors.add(location + "empty translation");
                if (hasLetters(words(original)) && !hasLetters(words(value))) errors.add(location + "translation has no words");
                if (words(value).startsWith(". ")) errors.add(location + "translation begins with a missing sentence");
                if (language.equals("en") && !value.equals(original)) errors.add(location + "English differs from the fallback catalog");
                if (!language.equals("en") && words(value).equals(words(original))
                        && ENGLISH_WORD.matcher(words(value)).find()
                        && !(language.equals("id") && SHARED_INDONESIAN_WORDS.contains(key))) {
                    errors.add(location + "untranslated English text");
                }
            }
        }

        Set<String> supported = new TreeSet<>();
        for (Element locale : children(parse(res.resolve("xml/locales_config.xml")), "locale")) {
            supported.add(normalize(locale.getAttributeNS("http://schemas.android.com/apk/res/android", "name")));
        }
        if (!supported.equals(new TreeSet<>(LANGUAGES.values()))) {
            errors.add("locales_config.xml does not match the translated catalogs");
        }
        String gradle = Files.readString(root.resolve("app/build.gradle.kts"));
        var filters = Pattern.compile("localeFilters\\s*\\+=\\s*listOf\\(([^)]*)\\)").matcher(gradle);
        Set<String> filteredLocales = new TreeSet<>();
        if (filters.find()) {
            var codes = Pattern.compile("\"([a-z]+)\"").matcher(filters.group(1));
            while (codes.find()) filteredLocales.add(normalize(codes.group(1)));
        }
        if (!filteredLocales.equals(supported)) errors.add("Gradle localeFilters must match locales_config.xml");
        if (!Pattern.compile("language\\s*\\{\\s*enableSplit\\s*=\\s*false").matcher(gradle).find()) {
            errors.add("Bundle language splits must be disabled for the offline language picker");
        }

        Path sources = root.resolve("app/src/main/java");
        if (Files.isDirectory(sources)) {
            try (var paths = Files.walk(sources)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".kt")).sorted().collect(Collectors.toList())) {
                    String source = Files.readString(path);
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    var literal = UI_LITERAL.matcher(source);
                    while (literal.find()) {
                        if (hasLetters(literal.group(1))) {
                            long line = source.substring(0, literal.start()).chars().filter(c -> c == '\n').count() + 1;
                            errors.add(relative + ":" + line + ": hardcoded UI text");
                        }
                    }
                    String[] lines = source.split("\\R", -1);
                    for (int i = 0; i < lines.length; i++) {
                        // Storage keys, shader source, test tags and diagnostics are not UI text.
                        if (RUSSIAN.matcher(lines[i]).find() && lines[i].contains("\"")) {
                            errors.add(relative + ":" + (i + 1) + ": hardcoded Russian text");
                        }
                        if (PINNED_LOCALE.matcher(lines[i]).find()) errors.add(relative + ":" + (i + 1) + ": pinned locale");
                    }
                }
            }
        }
        return new Report(translatable.size(), errors);
    }

    private static Map<String, Element> read(Path directory, List<String> errors) throws Exception {
        Map<String, Element> values = new TreeMap<>();
        if (!Files.isDirectory(directory)) return values;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).sorted().collect(Collectors.toList())) {
                for (Element string : children(parse(path), "string")) {
                    if (values.put(string.getAttribute("name"), string) != null) {
                        errors.add(directory.getFileName() + ": duplicate resource names");
                    }
                }
            }
        }
        return values;
    }

    private static Element parse(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var input = Files.newInputStream(path)) {
            return factory.newDocumentBuilder().parse(input).getDocumentElement();
        }
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        var nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element && ((Element) nodes.item(i)).getTagName().equals(tag)) {
                result.add((Element) nodes.item(i));
            }
        }
        return result;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static List<String> placeholders(String value) {
        List<String> result = new ArrayList<>();
        var matcher = ARGUMENT.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        Collections.sort(result);
        return result;
    }

    private static String unquote(String value) { return value.replaceAll("^[\" ]+|[\" ]+$", ""); }
    private static String words(String value) { return unquote(ARGUMENT.matcher(value).replaceAll("")); }
    private static boolean hasLetters(String value) { return value.codePoints().anyMatch(Character::isLetter); }
    private static String normalize(String code) { return code.equals("in") ? "id" : code; }

    static final class Report {
        final int stringCount;
        final List<String> errors;
        Report(int stringCount, List<String> errors) {
            this.stringCount = stringCount;
            this.errors = List.copyOf(errors);
        }
    }
}
