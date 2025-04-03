package com.pinescript.plugin.editor;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.pinescript.plugin.language.PineScriptLexer;
import com.pinescript.plugin.psi.PineScriptTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class PineScriptSyntaxHighlighter extends SyntaxHighlighterBase {
    // TradingView-like colors based on the screenshot
    private static final Color KEYWORD_COLOR = new Color(86, 156, 214);      // Blue for keywords (var, if)
    private static final Color STRING_COLOR = new Color(214, 157, 133);      // Orange-brown for strings
    private static final Color NUMBER_COLOR = new Color(181, 206, 168);      // Light green for numbers
    private static final Color COMMENT_COLOR = new Color(87, 166, 74);       // Green for comments
    private static final Color FUNCTION_COLOR = new Color(220, 220, 170);    // Light yellow for functions
    private static final Color OPERATOR_COLOR = new Color(180, 180, 180);    // Light gray for operators
    private static final Color IDENTIFIER_COLOR = new Color(156, 220, 254);  // Light cyan for identifiers
    private static final Color TYPE_COLOR = new Color(86, 156, 214);         // Blue for types (float, bool)
    private static final Color NAMESPACE_COLOR = new Color(78, 201, 176);    // Teal for namespaces
    private static final Color BUILT_IN_VAR_COLOR = new Color(189, 183, 107); // Khaki/yellow for built-in variables (open, high, low, close)
    
    // Special colors for specific elements
    private static final Color TRUE_FALSE_COLOR = new Color(86, 156, 214);   // Blue for true/false
    private static final Color NA_COLOR = new Color(86, 156, 214);           // Blue for na values
    private static final Color PLOT_COLOR_RED = new Color(227, 110, 110);    // Red for plot color
    private static final Color PLOT_COLOR_BLUE = new Color(111, 168, 220);   // Blue for plot color
    private static final Color COLOR_CONSTANTS = new Color(214, 157, 133);   // Orange-brown for color constants
    private static final Color EXIT_LABEL_COLOR = new Color(227, 110, 110);  // Red for exit labels

    // Create TextAttributes with TradingView-like colors
    private static final TextAttributes KEYWORD_ATTRIBUTES = new TextAttributes(KEYWORD_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes STRING_ATTRIBUTES = new TextAttributes(STRING_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes NUMBER_ATTRIBUTES = new TextAttributes(NUMBER_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes COMMENT_ATTRIBUTES = new TextAttributes(COMMENT_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes FUNCTION_ATTRIBUTES = new TextAttributes(FUNCTION_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes OPERATOR_ATTRIBUTES = new TextAttributes(OPERATOR_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes IDENTIFIER_ATTRIBUTES = new TextAttributes(IDENTIFIER_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes TYPE_ATTRIBUTES = new TextAttributes(TYPE_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes NAMESPACE_ATTRIBUTES = new TextAttributes(NAMESPACE_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes TRUE_FALSE_ATTRIBUTES = new TextAttributes(TRUE_FALSE_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes NA_ATTRIBUTES = new TextAttributes(NA_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes COLOR_CONSTANT_ATTRIBUTES = new TextAttributes(COLOR_CONSTANTS, null, null, null, Font.PLAIN);
    private static final TextAttributes EXIT_LABEL_ATTRIBUTES = new TextAttributes(EXIT_LABEL_COLOR, null, null, null, Font.PLAIN);
    private static final TextAttributes BUILT_IN_VAR_ATTRIBUTES = new TextAttributes(BUILT_IN_VAR_COLOR, null, null, null, Font.PLAIN);

    // Create TextAttributesKey with custom attributes
    public static final TextAttributesKey KEYWORD = createTextAttributesKey("PINE_SCRIPT_KEYWORD", KEYWORD_ATTRIBUTES);
    public static final TextAttributesKey STRING = createTextAttributesKey("PINE_SCRIPT_STRING", STRING_ATTRIBUTES);
    public static final TextAttributesKey NUMBER = createTextAttributesKey("PINE_SCRIPT_NUMBER", NUMBER_ATTRIBUTES);
    public static final TextAttributesKey COMMENT = createTextAttributesKey("PINE_SCRIPT_COMMENT", COMMENT_ATTRIBUTES);
    public static final TextAttributesKey FUNCTION = createTextAttributesKey("PINE_SCRIPT_FUNCTION", FUNCTION_ATTRIBUTES);
    public static final TextAttributesKey OPERATOR = createTextAttributesKey("PINE_SCRIPT_OPERATOR", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey IDENTIFIER = createTextAttributesKey("PINE_SCRIPT_IDENTIFIER", IDENTIFIER_ATTRIBUTES);
    public static final TextAttributesKey TYPE = createTextAttributesKey("PINE_SCRIPT_TYPE", TYPE_ATTRIBUTES);
    public static final TextAttributesKey NAMESPACE = createTextAttributesKey("PINE_SCRIPT_NAMESPACE", NAMESPACE_ATTRIBUTES);
    public static final TextAttributesKey TRUE_FALSE = createTextAttributesKey("PINE_SCRIPT_TRUE_FALSE", TRUE_FALSE_ATTRIBUTES);
    public static final TextAttributesKey NA = createTextAttributesKey("PINE_SCRIPT_NA", NA_ATTRIBUTES);
    public static final TextAttributesKey COLOR_CONSTANT = createTextAttributesKey("PINE_SCRIPT_COLOR_CONSTANT", COLOR_CONSTANT_ATTRIBUTES);
    public static final TextAttributesKey EXIT_LABEL = createTextAttributesKey("PINE_SCRIPT_EXIT_LABEL", EXIT_LABEL_ATTRIBUTES);
    public static final TextAttributesKey BUILT_IN_VARIABLE = createTextAttributesKey("PINE_SCRIPT_BUILT_IN_VARIABLE", BUILT_IN_VAR_ATTRIBUTES);
    
    // Use default colors for these
    public static final TextAttributesKey PARENTHESES = createTextAttributesKey("PINE_SCRIPT_PARENTHESES", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey BRACKETS = createTextAttributesKey("PINE_SCRIPT_BRACKETS", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey BRACES = createTextAttributesKey("PINE_SCRIPT_BRACES", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey COMMA = createTextAttributesKey("PINE_SCRIPT_COMMA", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey DOT = createTextAttributesKey("PINE_SCRIPT_DOT", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey SEMICOLON = createTextAttributesKey("PINE_SCRIPT_SEMICOLON", OPERATOR_ATTRIBUTES);
    public static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey("PINE_SCRIPT_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    // Token sets for more efficient handling
    private static final TokenSet KEYWORD_TOKENS = TokenSet.create(PineScriptTokenTypes.KEYWORD);
    private static final TokenSet STRING_TOKENS = TokenSet.create(PineScriptTokenTypes.STRING);
    private static final TokenSet NUMBER_TOKENS = TokenSet.create(PineScriptTokenTypes.NUMBER);
    private static final TokenSet COMMENT_TOKENS = TokenSet.create(PineScriptTokenTypes.COMMENT);
    private static final TokenSet FUNCTION_TOKENS = TokenSet.create(PineScriptTokenTypes.FUNCTION);
    private static final TokenSet OPERATOR_TOKENS = TokenSet.create(PineScriptTokenTypes.OPERATOR);
    private static final TokenSet IDENTIFIER_TOKENS = TokenSet.create(PineScriptTokenTypes.IDENTIFIER);
    private static final TokenSet TYPE_TOKENS = TokenSet.create(PineScriptTokenTypes.TYPE);
    private static final TokenSet NAMESPACE_TOKENS = TokenSet.create(PineScriptTokenTypes.NAMESPACE);
    private static final TokenSet TRUE_FALSE_TOKENS = TokenSet.create(PineScriptTokenTypes.TRUE_FALSE);
    private static final TokenSet NA_TOKENS = TokenSet.create(PineScriptTokenTypes.NA);
    private static final TokenSet COLOR_CONSTANT_TOKENS = TokenSet.create(PineScriptTokenTypes.COLOR_CONSTANT);
    private static final TokenSet EXIT_LABEL_TOKENS = TokenSet.create(PineScriptTokenTypes.EXIT_LABEL);
    private static final TokenSet BUILT_IN_VAR_TOKENS = TokenSet.create(PineScriptTokenTypes.BUILT_IN_VARIABLE);
    private static final TokenSet BAD_CHAR_TOKENS = TokenSet.create(TokenType.BAD_CHARACTER);

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new PineScriptLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == null) return TextAttributesKey.EMPTY_ARRAY;

        if (KEYWORD_TOKENS.contains(tokenType)) {
            return pack(KEYWORD);
        } else if (IDENTIFIER_TOKENS.contains(tokenType)) {
            return pack(IDENTIFIER);
        } else if (COMMENT_TOKENS.contains(tokenType)) {
            return pack(COMMENT);
        } else if (STRING_TOKENS.contains(tokenType)) {
            return pack(STRING);
        } else if (NUMBER_TOKENS.contains(tokenType)) {
            return pack(NUMBER);
        } else if (OPERATOR_TOKENS.contains(tokenType)) {
            return pack(OPERATOR);
        } else if (FUNCTION_TOKENS.contains(tokenType)) {
            return pack(FUNCTION);
        } else if (NAMESPACE_TOKENS.contains(tokenType)) {
            return pack(NAMESPACE);
        } else if (TYPE_TOKENS.contains(tokenType)) {
            return pack(TYPE);
        } else if (TRUE_FALSE_TOKENS.contains(tokenType)) {
            return pack(TRUE_FALSE);
        } else if (NA_TOKENS.contains(tokenType)) {
            return pack(NA);
        } else if (COLOR_CONSTANT_TOKENS.contains(tokenType)) {
            return pack(COLOR_CONSTANT);
        } else if (EXIT_LABEL_TOKENS.contains(tokenType)) {
            return pack(EXIT_LABEL);
        } else if (BUILT_IN_VAR_TOKENS.contains(tokenType)) {
            return pack(BUILT_IN_VARIABLE);
        } else if (BAD_CHAR_TOKENS.contains(tokenType)) {
            return pack(BAD_CHARACTER);
        }

        // Handle punctuation
        if (tokenType.equals(PineScriptTokenTypes.LPAREN) || tokenType.equals(PineScriptTokenTypes.RPAREN)) {
            return pack(PARENTHESES);
        } else if (tokenType.equals(PineScriptTokenTypes.LBRACKET) || tokenType.equals(PineScriptTokenTypes.RBRACKET)) {
            return pack(BRACKETS);
        } else if (tokenType.equals(PineScriptTokenTypes.LBRACE) || tokenType.equals(PineScriptTokenTypes.RBRACE)) {
            return pack(BRACES);
        } else if (tokenType.equals(PineScriptTokenTypes.COMMA)) {
            return pack(COMMA);
        } else if (tokenType.equals(PineScriptTokenTypes.DOT)) {
            return pack(DOT);
        } else if (tokenType.equals(PineScriptTokenTypes.SEMICOLON)) {
            return pack(SEMICOLON);
        }

        return TextAttributesKey.EMPTY_ARRAY;
    }
} 