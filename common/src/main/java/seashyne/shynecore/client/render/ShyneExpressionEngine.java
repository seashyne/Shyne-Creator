package seashyne.shynecore.client.render;

import seashyne.shynecore.ShyneCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small, deterministic expression engine used by animation keyframes.
 *
 * <p>It deliberately supports only math and read-only animation variables. No
 * reflection, Java access, allocation APIs or side effects are reachable from
 * an Avatar expression.</p>
 */
public final class ShyneExpressionEngine {
    private static final int MAX_CACHED_EXPRESSIONS = 16_384;
    private static final Map<String, Compiled> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private ShyneExpressionEngine() {}

    public static double evaluate(String source, AnimationExpressionContext context, double base) {
        String normalized = normalize(source);
        try {
            Compiled compiled = compiled(normalized);
            return compiled.root.eval(context == null ? AnimationExpressionContext.EMPTY : context, base);
        } catch (RuntimeException error) {
            if (REPORTED.add(normalized)) {
                ShyneCore.LOGGER.warn("[AnimationExpression] Unsupported expression '{}': {}", source, error.getMessage());
            }
            return base;
        }
    }

    public static String validate(String source) {
        try {
            compiled(normalize(source));
            return "";
        } catch (RuntimeException error) {
            return error.getMessage() == null ? "Unsupported expression" : error.getMessage();
        }
    }

    private static String normalize(String source) {
        String value = source == null || source.isBlank() ? "0" : source.trim();
        // Some legacy Blockbench expression providers use q(...) for the channel base.
        return value.replaceAll("(?i)q\\s*\\(\\s*\\.\\.\\.\\s*\\)", "base");
    }

    private static Compiled compiled(String source) {
        Compiled cached = CACHE.get(source);
        if (cached != null) return cached;
        Compiled result = compileUnchecked(source);
        // Public avatars are untrusted. Keep evaluation fast without allowing a
        // pack with thousands of unique expressions to grow this cache forever.
        if (CACHE.size() < MAX_CACHED_EXPRESSIONS) CACHE.putIfAbsent(source, result);
        return result;
    }

    private static Compiled compileUnchecked(String source) {
        Parser parser = new Parser(source);
        Node root = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.error("Unexpected token");
        return new Compiled(root);
    }

    @FunctionalInterface
    private interface Node {
        double eval(AnimationExpressionContext context, double base);
    }

    private record Compiled(Node root) {}

    private static final class Parser {
        private final String source;
        private int cursor;

        private Parser(String source) { this.source = source; }

        private Node parseExpression() { return parseAdditive(); }

        private Node parseAdditive() {
            Node left = parseMultiplicative();
            while (true) {
                skipWhitespace();
                if (take('+')) { Node a = left, b = parseMultiplicative(); left = (c, base) -> a.eval(c, base) + b.eval(c, base); }
                else if (take('-')) { Node a = left, b = parseMultiplicative(); left = (c, base) -> a.eval(c, base) - b.eval(c, base); }
                else return left;
            }
        }

        private Node parseMultiplicative() {
            Node left = parsePower();
            while (true) {
                skipWhitespace();
                if (take('*')) { Node a = left, b = parsePower(); left = (c, base) -> a.eval(c, base) * b.eval(c, base); }
                else if (take('/')) { Node a = left, b = parsePower(); left = (c, base) -> a.eval(c, base) / b.eval(c, base); }
                else if (take('%')) { Node a = left, b = parsePower(); left = (c, base) -> a.eval(c, base) % b.eval(c, base); }
                else return left;
            }
        }

        private Node parsePower() {
            Node left = parseUnary();
            skipWhitespace();
            if (!take('^')) return left;
            Node right = parsePower();
            return (c, base) -> Math.pow(left.eval(c, base), right.eval(c, base));
        }

        private Node parseUnary() {
            skipWhitespace();
            if (take('+')) return parseUnary();
            if (take('-')) { Node value = parseUnary(); return (c, base) -> -value.eval(c, base); }
            return parsePrimary();
        }

        private Node parsePrimary() {
            skipWhitespace();
            if (take('(')) {
                Node inside = parseExpression();
                skipWhitespace();
                if (!take(')')) throw error("Expected ')'");
                return inside;
            }
            if (cursor < source.length() && (Character.isDigit(source.charAt(cursor)) || source.charAt(cursor) == '.')) {
                return number();
            }
            String identifier = identifier();
            if (identifier.isEmpty()) throw error("Expected number, variable or function");
            skipWhitespace();
            if (!take('(')) return (c, base) -> c.resolve(identifier, base);

            List<Node> args = new ArrayList<>();
            skipWhitespace();
            if (!take(')')) {
                do {
                    args.add(parseExpression());
                    skipWhitespace();
                } while (take(','));
                if (!take(')')) throw error("Expected ')' after function arguments");
            }
            return function(identifier, args);
        }

        private Node number() {
            int start = cursor;
            while (cursor < source.length() && (Character.isDigit(source.charAt(cursor)) || ".eE+-".indexOf(source.charAt(cursor)) >= 0)) {
                char current = source.charAt(cursor);
                if ((current == '+' || current == '-') && cursor > start && source.charAt(cursor - 1) != 'e' && source.charAt(cursor - 1) != 'E') break;
                cursor++;
            }
            try {
                double value = Double.parseDouble(source.substring(start, cursor));
                return (c, base) -> value;
            } catch (NumberFormatException error) {
                throw error("Invalid number");
            }
        }

        private String identifier() {
            int start = cursor;
            while (cursor < source.length()) {
                char c = source.charAt(cursor);
                if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') break;
                cursor++;
            }
            return source.substring(start, cursor);
        }

        private Node function(String rawName, List<Node> args) {
            String name = rawName.toLowerCase(Locale.ROOT);
            if (name.startsWith("math.")) name = name.substring(5);
            String fn = name;
            if (!Set.of("sin", "cos", "tan", "asin", "acos", "atan", "abs", "sqrt", "floor", "ceil", "round", "sign", "signum", "pow", "min", "max", "clamp", "lerp").contains(fn)) {
                throw error("Unknown function " + rawName);
            }
            int expectedArguments = switch (fn) {
                case "pow", "min", "max" -> 2;
                case "clamp", "lerp" -> 3;
                default -> 1;
            };
            if (args.size() != expectedArguments) throw error(rawName + " expects " + expectedArguments + " argument(s)");
            return (context, base) -> {
                double[] v = new double[args.size()];
                for (int i = 0; i < args.size(); i++) v[i] = args.get(i).eval(context, base);
                return switch (fn) {
                    // Blockbench/Molang trigonometry uses degrees.
                    case "sin" -> Math.sin(Math.toRadians(one(fn, v)));
                    case "cos" -> Math.cos(Math.toRadians(one(fn, v)));
                    case "tan" -> Math.tan(Math.toRadians(one(fn, v)));
                    case "asin" -> Math.toDegrees(Math.asin(one(fn, v)));
                    case "acos" -> Math.toDegrees(Math.acos(one(fn, v)));
                    case "atan" -> Math.toDegrees(Math.atan(one(fn, v)));
                    case "abs" -> Math.abs(one(fn, v));
                    case "sqrt" -> Math.sqrt(one(fn, v));
                    case "floor" -> Math.floor(one(fn, v));
                    case "ceil" -> Math.ceil(one(fn, v));
                    case "round" -> Math.rint(one(fn, v));
                    case "sign", "signum" -> Math.signum(one(fn, v));
                    case "pow" -> Math.pow(at(fn, v, 0, 2), at(fn, v, 1, 2));
                    case "min" -> Math.min(at(fn, v, 0, 2), at(fn, v, 1, 2));
                    case "max" -> Math.max(at(fn, v, 0, 2), at(fn, v, 1, 2));
                    case "clamp" -> Math.max(at(fn, v, 1, 3), Math.min(at(fn, v, 2, 3), at(fn, v, 0, 3)));
                    case "lerp" -> at(fn, v, 0, 3) + (at(fn, v, 1, 3) - at(fn, v, 0, 3)) * at(fn, v, 2, 3);
                    default -> throw new IllegalArgumentException("Unknown function " + rawName);
                };
            };
        }

        private static double one(String name, double[] values) { return at(name, values, 0, 1); }
        private static double at(String name, double[] values, int index, int expected) {
            if (values.length != expected) throw new IllegalArgumentException(name + " expects " + expected + " argument(s)");
            return values[index];
        }

        private boolean take(char expected) {
            if (cursor < source.length() && source.charAt(cursor) == expected) { cursor++; return true; }
            return false;
        }
        private boolean atEnd() { return cursor >= source.length(); }
        private void skipWhitespace() { while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++; }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at column " + (cursor + 1)); }
    }
}
