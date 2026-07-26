package seashyne.shynecore.client.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Finds a free slot beside the small icon row on Minecraft's title screen. */
public final class TitleMenuButtonLayout {
    public static final int BUTTON_SIZE = 20;
    private static final int GAP = 4;
    private static final int EDGE_MARGIN = 4;
    private static final int MAX_ICON_WIDTH = 48;

    private TitleMenuButtonLayout() {}

    public static Optional<Position> findPosition(int screenWidth, int screenHeight, Collection<Bounds> widgets) {
        if (screenWidth < BUTTON_SIZE + EDGE_MARGIN * 2 || screenHeight < BUTTON_SIZE + EDGE_MARGIN * 2) {
            return Optional.empty();
        }

        List<Bounds> occupied = widgets == null ? List.of() : widgets.stream()
            .filter(Bounds::valid)
            .toList();
        int centerX = screenWidth / 2;
        Map<Integer, List<Bounds>> rows = new HashMap<>();
        for (Bounds widget : occupied) {
            if (widget.height() != BUTTON_SIZE || widget.width() > MAX_ICON_WIDTH) continue;
            int widgetCenter = widget.x() + widget.width() / 2;
            if (Math.abs(widgetCenter - centerX) > 140) continue;
            rows.computeIfAbsent(widget.y(), ignored -> new ArrayList<>()).add(widget);
        }

        int expectedY = screenHeight / 4 + 120;
        List<Bounds> iconRow = rows.values().stream()
            .filter(row -> row.size() >= 2)
            .max(Comparator
                .comparingInt((List<Bounds> row) -> row.size())
                .thenComparingInt(row -> -Math.abs(row.get(0).y() - expectedY)))
            .orElse(null);
        if (iconRow == null) return Optional.empty();

        int y = iconRow.get(0).y();
        int right = iconRow.stream().mapToInt(Bounds::right).max().orElse(centerX) + GAP;
        Position rightPosition = new Position(right, y);
        if (fits(rightPosition, screenWidth, screenHeight, occupied)) return Optional.of(rightPosition);

        int left = iconRow.stream().mapToInt(Bounds::x).min().orElse(centerX) - GAP - BUTTON_SIZE;
        Position leftPosition = new Position(left, y);
        if (fits(leftPosition, screenWidth, screenHeight, occupied)) return Optional.of(leftPosition);
        return Optional.empty();
    }

    private static boolean fits(Position position, int screenWidth, int screenHeight, List<Bounds> occupied) {
        Bounds candidate = new Bounds(position.x(), position.y(), BUTTON_SIZE, BUTTON_SIZE);
        if (candidate.x() < EDGE_MARGIN || candidate.y() < EDGE_MARGIN
            || candidate.right() > screenWidth - EDGE_MARGIN
            || candidate.bottom() > screenHeight - EDGE_MARGIN) {
            return false;
        }
        return occupied.stream().noneMatch(candidate::intersects);
    }

    public record Position(int x, int y) {}

    public record Bounds(int x, int y, int width, int height) {
        private boolean valid() {
            return width > 0 && height > 0;
        }

        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean intersects(Bounds other) {
            return x < other.right() && right() > other.x
                && y < other.bottom() && bottom() > other.y;
        }
    }
}
