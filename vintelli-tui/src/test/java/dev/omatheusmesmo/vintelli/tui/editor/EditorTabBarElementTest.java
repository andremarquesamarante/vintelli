package dev.omatheusmesmo.vintelli.tui.editor;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorTabBarElementTest {

    private static final int HEIGHT = EditorTabBarElement.ROWS;
    private static final String CLOSE = "x";
    private static final List<EditorTabBarElement.Label> LABELS = List.of(
            new EditorTabBarElement.Label(Span.raw("#"), "Alpha"),
            new EditorTabBarElement.Label(Span.raw("#"), "Beta"),
            new EditorTabBarElement.Label(Span.raw("#"), "Gamma"));

    @Test
    void theCloseIconShowsOnTheSelectedTabAndOnTheHoveredTabOnly() {
        EditorTabBarElement bar = bar(1, null);

        String selectedOnly = row(render(bar, 60), 0);
        assertEquals(1, count(selectedOnly, CLOSE), "only the selected tab shows its icon: " + selectedOnly);
        assertTrue(selectedOnly.contains("Beta " + CLOSE), selectedOnly);

        assertTrue(bar.hover(columnOf(selectedOnly, "Gamma"), 0));
        String hovered = row(render(bar, 60), 0);
        assertEquals(2, count(hovered, CLOSE), "the hovered tab shows its icon too: " + hovered);
        assertTrue(hovered.contains("Gamma " + CLOSE), hovered);

        assertTrue(bar.hover(0, 0));
        assertEquals(1, count(row(render(bar, 60), 0), CLOSE), "leaving the tabs hides the hover icon");
    }

    @Test
    void theIconIsPartOfTheTabWidthSoTheRowDoesNotMoveWhenItAppears() {
        EditorTabBarElement bar = bar(0, null);
        String before = row(render(bar, 60), 0);
        int gammaBefore = columnOf(before, "Gamma");
        int betaBefore = columnOf(before, "Beta");

        bar.hover(betaBefore, 0);
        String after = row(render(bar, 60), 0);

        assertEquals(gammaBefore, columnOf(after, "Gamma"), "the next tab keeps its column: " + after);
        assertEquals(betaBefore, columnOf(after, "Beta"));
    }

    @Test
    void theIconIsItsOwnHitAreaAndTheTitleStillSelects() {
        EditorTabBarElement bar = bar(0, null);
        String row = row(render(bar, 60), 0);
        int alphaTitle = columnOf(row, "Alpha");
        int alphaIcon = row.indexOf(CLOSE, alphaTitle);

        assertEquals(0, bar.tabAt(alphaTitle, 0));
        assertEquals(-1, bar.closeAt(alphaTitle, 0), "the title is not the close area");
        assertEquals(0, bar.closeAt(alphaIcon, 0));
        assertEquals(-1, bar.tabAt(alphaIcon, 0), "the icon does not select the tab");
        assertEquals(0, bar.tabUnder(alphaIcon, 0), "a middle click still finds the tab under the icon");
        assertEquals(-1, bar.closeAt(alphaIcon, 1), "only the label row closes");
    }

    @Test
    void aHiddenIconIsNotAHitAreaSoAClickThereSelectsTheTab() {
        EditorTabBarElement bar = bar(0, null);
        String row = row(render(bar, 60), 0);
        int betaIcon = columnOf(row, "Beta") + "Beta".length() + 1;

        assertEquals(-1, bar.closeAt(betaIcon, 0));
        assertEquals(1, bar.tabAt(betaIcon, 0));
    }

    @Test
    void hitTestingFollowsTheLastRenderedGeometryAfterAResize() {
        EditorTabBarElement bar = bar(0, null);
        String wide = row(render(bar, 80), 0);
        int gammaTitle = columnOf(wide, "Gamma");
        bar.hover(gammaTitle, 0);
        render(bar, 80);
        int gammaIcon = row(render(bar, 80), 0).indexOf(CLOSE, gammaTitle);
        assertEquals(2, bar.closeAt(gammaIcon, 0));

        render(bar, 24);

        assertEquals(-1, bar.closeAt(gammaIcon, 0), "the icon of a tab that is no longer drawn closes nothing");
        assertEquals(-1, bar.tabAt(gammaTitle, 0));
        int alphaIcon = row(render(bar, 24), 0).indexOf(CLOSE);
        assertEquals(0, bar.closeAt(alphaIcon, 0), "the icon of the drawn selected tab still closes it");
    }

    @Test
    void aPendingPromptReplacesTheTabsAndWrapsOverBothRows() {
        String prompt = "Discard unsaved changes to src/A.java? [y/N]";
        EditorTabBarElement bar = bar(0, prompt);

        Buffer wide = render(bar, 60);
        assertTrue(row(wide, 0).contains(prompt), row(wide, 0));
        assertEquals(-1, bar.tabAt(columnOf(row(wide, 0), "Discard"), 0));
        assertEquals(-1, bar.closeAt(columnOf(row(wide, 0), "Discard"), 0));

        Buffer narrow = render(bar, 30);
        String joined = row(narrow, 0).substring(2) + row(narrow, 1).substring(2).stripTrailing();
        assertEquals(prompt, joined, "a narrow bar keeps the whole question on its two rows");
        assertFalse(row(narrow, 0).contains("Alpha"));
    }

    private static EditorTabBarElement bar(int selected, String prompt) {
        EditorTabBarElement bar = new EditorTabBarElement();
        bar.update(LABELS, selected, false, new EditorTabBarElement.Glyphs(" ", "-", ">", CLOSE), prompt);
        return bar;
    }

    private static Buffer render(EditorTabBarElement bar, int width) {
        Rect area = new Rect(0, 0, width, HEIGHT);
        Buffer buffer = Buffer.empty(area);
        bar.render(Frame.forTesting(buffer), area, DefaultRenderContext.createEmpty());
        return buffer;
    }

    private static String row(Buffer buffer, int y) {
        StringBuilder text = new StringBuilder();
        for (int x = 0; x < buffer.width(); x++) {
            text.append(buffer.get(x, y).symbol());
        }
        return text.toString();
    }

    private static int columnOf(String row, String text) {
        int column = row.indexOf(text);
        assertTrue(column >= 0, "missing " + text + " in " + row);
        return column;
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
