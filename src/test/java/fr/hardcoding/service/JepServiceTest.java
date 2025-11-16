package fr.hardcoding.service;

import fr.hardcoding.model.Jep;
import fr.hardcoding.model.JepState;
import fr.hardcoding.model.JepType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static fr.hardcoding.model.JepState.TARGETED;
import static fr.hardcoding.model.JepType.FEATURE;
import static fr.hardcoding.service.JepService.parseJeps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JepServiceTest {
    @Test
    void testParseValidJep() {
        String html = createHtmlTable("""
                <tr>
                    <td>F</td>
                    <td>Tar</td>
                    <td>25</td>
                    <td><span class="cl">security</span><span class="cr">crypto</span></td>
                    <td class="jep">470</td>
                    <td>PEM Encodings of Cryptographic Objects</td>
                </tr>
                """);
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertEquals(1, jeps.size());
        Jep jep = jeps.getFirst();
        assertEquals(FEATURE, jep.type);
        assertEquals(TARGETED, jep.state);
        assertEquals("25", jep.release);
        assertEquals("security", jep.component);
        assertEquals("crypto", jep.subComponent);
        assertEquals("470", jep.number);
        assertEquals("PEM Encodings of Cryptographic Objects", jep.title);
    }

    @Test
    void testSkipPartialRow() {
        String html = createHtmlTable("""
                <tr>
                    <td>F</td>
                    <td>Dra</td>
                    <td></td>
                </tr>
                """);
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertTrue(jeps.isEmpty(), "Row with < 5 cells should be skipped");
    }

    @ParameterizedTest
    @EnumSource(JepType.class)
    void testParseJepType(JepType jepType) {
        String html = createHtmlTable("""
                <tr>
                    <td>%s</td>
                    <td>Act</td>
                    <td></td>
                    <td></td>
                    <td class="jep">100</td>
                    <td>Test JEP</td>
                </tr>
                """.formatted(jepType.shortName()));
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertEquals(1, jeps.size(), "Failed to parse the JEP");
        assertEquals(jepType, jeps.getFirst().type, "Failed to parse the JEP type");
    }

    @Test
    void testSkipInvalidJepType() {
        String html = createHtmlTable("""
                <tr>
                    <td>X</td>
                    <td>Dra</td>
                    <td></td>
                    <td></td>
                    <td class="jep">100</td>
                    <td>Invalid Type</td>
                </tr>
                """);
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertTrue(jeps.isEmpty(), "Invalid JEP type should be skipped");
    }

    @ParameterizedTest
    @EnumSource(JepState.class)
    void testParseJepState(JepState jepState) {
        String html = createHtmlTable("""
                <tr>
                    <td>F</td>
                    <td>%s</td>
                    <td></td>
                    <td></td>
                    <td class="jep">100</td>
                    <td>Test JEP</td>
                </tr>
                """.formatted(jepState.shortName()));
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertEquals(1, jeps.size(), "Failed to parse the JEP");
        assertEquals(jepState, jeps.getFirst().state, "Failed to parse the JEP state");
    }

    @Test
    void testSkipInvalidJepState() {
        String html = createHtmlTable("""
                <tr>
                    <td>F</td>
                    <td>XXX</td>
                    <td></td>
                    <td></td>
                    <td class="jep">100</td>
                    <td>Invalid State</td>
                </tr>
                """);
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertTrue(jeps.isEmpty(), "Invalid JEP state should be skipped");
    }

    @ParameterizedTest(name = "testParseComponent ({1} / {2})")
    @MethodSource("componentArguments")
    void testParseComponent(String componentHtml, String component, String subComponent) {
        String html = createHtmlTable("""
                <tr>
                    <td>F</td>
                    <td>Dra</td>
                    <td></td>
                    %s
                    <td class="jep">100</td>
                    <td>Test JEP</td>
                </tr>
                """).formatted(componentHtml);
        Document doc = Jsoup.parse(html);
        List<Jep> jeps = parseJeps(doc);

        assertEquals(1, jeps.size());
        Jep jep = jeps.getFirst();
        assertEquals(component, jep.component, "Failed to parse the JEP component");
        assertEquals(subComponent, jep.subComponent, "Failed to parse the JEP subComponent");
    }

    private static Stream<Arguments> componentArguments() {
        return Stream.of(
                Arguments.arguments("""
                        <td xmlns="" class="cl">spec</td><td xmlns="" class="cm">/</td><td xmlns="" class="cr">lang</td>
                        """, "spec", "lang"),
                Arguments.arguments("""
                        <td xmlns="" class="cl">core</td><td xmlns="" class="cm">/</td><td xmlns="" class="cr">—</td>
                        """, "core", null)
        );
    }

    @Test
    void testParseJepsFromSample() throws IOException, URISyntaxException {
        Document document = Jsoup.parse(readTestResource("/sample.html"));
        List<Jep> jeps = parseJeps(document);
        assertFalse(jeps.isEmpty(), "Failed to parse JEPs");
        assertEquals(489, jeps.size(), "Failed to parse all JEPs");
    }

    private static String createHtmlTable(String rows) {
        return """
                <html>
                <body>
                <table class="jeps">
                %s
                </table>
                </body>
                </html>
                """.formatted(rows);
    }

    private static String readTestResource(String resource) throws IOException, URISyntaxException {
        URI uri = Objects.requireNonNull(JepServiceTest.class.getResource(resource), "Invalid resource").toURI();
        return Files.readString(Path.of(uri), StandardCharsets.ISO_8859_1);
    }
}
