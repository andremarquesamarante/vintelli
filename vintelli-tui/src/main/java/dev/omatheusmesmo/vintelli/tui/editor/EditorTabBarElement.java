package dev.omatheusmesmo.vintelli.tui.editor;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.css.cascade.PseudoClassState;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab row of the built-in editor: the open files on the first row, an accent
 * underline under the selected label on the second. The first column of the
 * label row holds the pane focus mark, so the labels start two columns in.
 *
 * <p>Tabs that do not fit are clipped and the window scrolls, keeping the
 * selected tab visible; a {@code »} glyph on the right marks more tabs. Each tab ends
 * with a close icon that is part of its width: it shows on the selected tab and on the hovered
 * one, and is a separate hit area from the title. While a close prompt is pending it takes the bar
 * over. The styles come from the {@code EditorTabBarElement-*} rules of the active theme.
 */
final class EditorTabBarElement extends StyledElement<EditorTabBarElement> {

    static final int ROWS = 2;

    private static final int LEAD = 2;
    private static final int GAP = 2;
    private static final int OVERFLOW_RESERVE = 2;

    record Label(Span icon, String text) {
    }

    record Glyphs(String focusMark, String underline, String overflow, String close) {
    }

    private record Window(int first, int last, boolean overflowRight) {
    }

    private record Slot(int index, int start, int end, int closeStart, int closeEnd) {
        boolean hasClose() {
            return closeStart >= 0;
        }

        boolean inClose(int x) {
            return hasClose() && x >= closeStart && x < closeEnd;
        }
    }

    private List<Label> labels = List.of();
    private int selected = -1;
    private boolean focused;
    private Glyphs glyphs = new Glyphs(" ", "-", ">", "x");
    private String prompt;
    private int hovered = -1;
    private int firstVisible;
    private List<Slot> slots = List.of();
    private Rect renderedArea = Rect.ZERO;

    EditorTabBarElement() {
        id("editor-tabs");
    }

    void update(List<Label> labels, int selected, boolean focused, Glyphs glyphs, String prompt) {
        this.labels = List.copyOf(labels);
        this.selected = this.labels.isEmpty() ? -1 : Math.clamp(selected, 0, this.labels.size() - 1);
        this.focused = focused;
        this.glyphs = glyphs;
        this.prompt = prompt;
    }

    /**
     * The tab whose title is under a click on the label row, or -1 for the underline row, the
     * overflow glyph, the focus column, the gaps, the close icon and everything past the last tab.
     */
    int tabAt(int x, int y) {
        Slot slot = slotAt(x, y);
        return slot == null || slot.inClose(x) ? -1 : slot.index();
    }

    /** The tab whose visible close icon is under the point, or -1. */
    int closeAt(int x, int y) {
        Slot slot = slotAt(x, y);
        return slot != null && slot.inClose(x) ? slot.index() : -1;
    }

    /** The tab under the point on the label row, close icon included, or -1. */
    int tabUnder(int x, int y) {
        Slot slot = slotAt(x, y);
        return slot == null ? -1 : slot.index();
    }

    /** Tracks the hovered tab from the last rendered geometry; returns whether it changed. */
    boolean hover(int x, int y) {
        int tab = tabUnder(x, y);
        boolean changed = tab != hovered;
        hovered = tab;
        return changed;
    }

    private Slot slotAt(int x, int y) {
        if (renderedArea.isEmpty() || y != renderedArea.y()) {
            return null;
        }
        for (Slot slot : slots) {
            if (x >= slot.start() && x < slot.end()) {
                return slot;
            }
        }
        return null;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        renderedArea = area;
        slots = List.of();
        if (labels.isEmpty() || area.isEmpty()) {
            return;
        }
        Buffer buffer = frame.buffer();
        int rows = Math.min(ROWS, area.height());
        Style base = context.currentStyle();
        buffer.fill(new Rect(area.x(), area.y(), area.width(), rows), new Cell(" ", base));

        int labelRow = area.y();
        Style markerStyle = context.childStyle("marker");
        draw(buffer, area.x(), area.right(), labelRow, focused ? glyphs.focusMark() : " ", markerStyle);

        Style ruleStyle = Style.EMPTY.underlined().underlineColor(context.childStyle("header-rule").fg().orElse(null));
        if (prompt != null) {
            renderPrompt(buffer, area, rows, context.childStyle("prompt"), ruleStyle);
            return;
        }

        Style tabStyle = context.childStyle("tab");
        Style selectedStyle = context.childStyle("tab", PseudoClassState.ofSelected());
        Style underlineStyle = context.childStyle("underline");
        Style closeStyle = context.childStyle("close");
        Style closeActiveStyle = context.childStyle("close", PseudoClassState.ofSelected());
        int closeWidth = CharWidth.of(glyphs.close());

        int[] widths = new int[labels.size()];
        for (int index = 0; index < widths.length; index++) {
            widths[index] = labelWidth(labels.get(index), closeWidth);
        }
        Window window = window(widths, selected, firstVisible, area.width() - LEAD);
        firstVisible = window.first();
        int limit = window.overflowRight() ? area.right() - OVERFLOW_RESERVE : area.right();

        List<Slot> visible = new ArrayList<>();
        int x = area.x() + LEAD;
        for (int index = window.first(); index <= window.last() && x < limit; index++) {
            if (index > window.first()) {
                x += GAP;
            }
            Label label = labels.get(index);
            boolean isSelected = index == selected;
            int start = x;
            Style iconStyle = base.patch(label.icon().style());
            x = draw(buffer, x, limit, labelRow, label.icon().content(), iconStyle);
            x = draw(buffer, x, limit, labelRow, " " + label.text(), isSelected ? selectedStyle : tabStyle);
            x = draw(buffer, x, limit, labelRow, " ", tabStyle);
            boolean closeVisible = isSelected || index == hovered;
            int closeStart = x;
            x = draw(buffer, x, limit, labelRow, closeVisible ? glyphs.close() : " ".repeat(closeWidth),
                    closeVisible ? closeActiveStyle : closeStyle);
            boolean closeDrawn = closeVisible && x - closeStart == closeWidth;
            if (isSelected && rows > 1) {
                draw(buffer, start, limit, labelRow + 1, glyphs.underline().repeat(x - start), underlineStyle);
            }
            visible.add(new Slot(index, start, x, closeDrawn ? closeStart : -1, closeDrawn ? x : -1));
        }
        buffer.setStyle(new Rect(area.x(), labelRow, area.width(), 1), ruleStyle);
        if (window.overflowRight()) {
            draw(buffer, area.right() - 1, area.right(), labelRow, glyphs.overflow(), context.childStyle("overflow"));
        }
        slots = List.copyOf(visible);
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return Size.of(availableWidth, labels.isEmpty() ? 0 : ROWS);
    }

    /**
     * Draws the close prompt over both rows of the bar, wrapped to the width, so a narrow pane still
     * shows the whole question. No tab is drawn while it is up, so no click can reach one.
     */
    private void renderPrompt(Buffer buffer, Rect area, int rows, Style promptStyle, Style ruleStyle) {
        int labelRow = area.y();
        int width = Math.max(1, area.width() - LEAD);
        String first = CharWidth.substringByWidth(prompt, width);
        draw(buffer, area.x() + LEAD, area.right(), labelRow, first, promptStyle);
        buffer.setStyle(new Rect(area.x(), labelRow, area.width(), 1), ruleStyle);
        if (rows > 1) {
            String rest = prompt.substring(first.length());
            draw(buffer, area.x() + LEAD, area.right(), labelRow + 1, CharWidth.substringByWidth(rest, width),
                    promptStyle);
        }
    }

    private static int labelWidth(Label label, int closeWidth) {
        return CharWidth.of(label.icon().content()) + 1 + CharWidth.of(label.text()) + 1 + closeWidth;
    }

    private static int draw(Buffer buffer, int x, int limit, int y, String text, Style style) {
        if (x >= limit) {
            return x;
        }
        String clipped = CharWidth.substringByWidth(text, limit - x);
        if (clipped.isEmpty()) {
            return x;
        }
        buffer.setString(x, y, clipped, style);
        return x + CharWidth.of(clipped);
    }

    /**
     * The tabs shown from {@code first} on: the scroll position moves back to
     * the selected tab when it is left of the window and forward until the
     * selected tab fits, so the selection is always inside the window.
     */
    private static Window window(int[] widths, int selected, int firstVisible, int region) {
        int count = widths.length;
        int first = Math.clamp(firstVisible, 0, count - 1);
        if (selected < first) {
            first = selected;
        }
        while (true) {
            int last = lastFitting(widths, first, region);
            if (last >= count - 1) {
                return new Window(first, count - 1, false);
            }
            int lastBeforeOverflow = lastFitting(widths, first, region - OVERFLOW_RESERVE);
            if (lastBeforeOverflow >= selected) {
                return new Window(first, lastBeforeOverflow, true);
            }
            first++;
        }
    }

    /** The last tab that fits the budget starting at {@code first}; the first tab always counts, clipped if needed. */
    private static int lastFitting(int[] widths, int first, int budget) {
        int used = 0;
        int last = first;
        for (int index = first; index < widths.length; index++) {
            int add = (index == first ? 0 : GAP) + widths[index];
            if (index > first && used + add > budget) {
                break;
            }
            used += add;
            last = index;
        }
        return last;
    }
}
