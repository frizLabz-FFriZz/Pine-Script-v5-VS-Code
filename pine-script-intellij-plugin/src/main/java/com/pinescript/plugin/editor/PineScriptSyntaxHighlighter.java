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
import com.intellij.util.ui.UIUtil;
import com.pinescript.plugin.language.PineScriptLexer;
import com.pinescript.plugin.psi.PineScriptTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class PineScriptSyntaxHighlighter extends SyntaxHighlighterBase {
    // Light theme colors (TradingView-like colors)
    private static final Color LIGHT_KEYWORD_COLOR = new Color(83, 169, 149);      // Green for keywords
    private static final Color LIGHT_STRING_COLOR = new Color(80, 140, 70);        // Warm green for strings
    private static final Color LIGHT_NUMBER_COLOR = new Color(230, 133, 55);       // Orange for numbers
    private static final Color LIGHT_COMMENT_COLOR = new Color(156, 156, 156);     // Grey for comments
    private static final Color LIGHT_FUNCTION_COLOR = new Color(56, 97, 246);      // Blue for functions
    private static final Color LIGHT_OPERATOR_COLOR = new Color(83, 169, 149);     // Green for operators
    private static final Color LIGHT_IDENTIFIER_COLOR = new Color(46, 46, 46);     // Black for variables
    private static final Color LIGHT_TYPE_COLOR = new Color(188, 57, 50);          // Red-Orange for types
    private static final Color LIGHT_NAMESPACE_COLOR = new Color(83, 169, 149);    // Green for namespaces
    private static final Color LIGHT_BUILT_IN_VAR_COLOR = new Color(188, 57, 50);  // Red-Orange for built-in variables
    
    // Dark theme colors
    private static final Color DARK_KEYWORD_COLOR = new Color(83, 169, 149);       // Green for keywords
    private static final Color DARK_STRING_COLOR = new Color(80, 140, 70);         // Warm green for strings
    private static final Color DARK_NUMBER_COLOR = new Color(230, 133, 55);        // Orange for numbers
    private static final Color DARK_COMMENT_COLOR = new Color(114, 114, 114);      // Darker grey for comments (TradingView match)
    private static final Color DARK_FUNCTION_COLOR = new Color(106, 154, 239);     // Blue for functions (TradingView match)
    private static final Color DARK_OPERATOR_COLOR = new Color(83, 169, 149);      // Green for operators
    private static final Color DARK_IDENTIFIER_COLOR = new Color(210, 210, 210);   // Light gray for variables
    private static final Color DARK_TYPE_COLOR = new Color(188, 57, 50);           // Red-Orange for types
    private static final Color DARK_NAMESPACE_COLOR = new Color(83, 169, 149);     // Green for namespaces
    private static final Color DARK_BUILT_IN_VAR_COLOR = new Color(231, 131, 131); // Red for built-in variables (TradingView match)
    
    // Special colors for specific elements
    private static final Color TRUE_FALSE_COLOR = new Color(66, 165, 245);    // Blue for true/false
    private static final Color NA_COLOR = new Color(66, 165, 245);            // Blue for na values
    private static final Color PLOT_COLOR_RED = new Color(255, 105, 95);      // Red for plot color
    private static final Color PLOT_COLOR_BLUE = new Color(107, 165, 220);    // Blue for plot color
    private static final Color COLOR_CONSTANTS = new Color(198, 120, 93);     // Orange-brown for color constants
    private static final Color EXIT_LABEL_COLOR = new Color(255, 105, 95);    // Red for exit labels

    // Dynamic color getters based on current theme
    private Color getKeywordColor() {
        return isDarkTheme() ? DARK_KEYWORD_COLOR : LIGHT_KEYWORD_COLOR;
    }
    
    private Color getStringColor() {
        return isDarkTheme() ? DARK_STRING_COLOR : LIGHT_STRING_COLOR;
    }
    
    private Color getNumberColor() {
        return isDarkTheme() ? DARK_NUMBER_COLOR : LIGHT_NUMBER_COLOR;
    }
    
    private Color getCommentColor() {
        return isDarkTheme() ? DARK_COMMENT_COLOR : LIGHT_COMMENT_COLOR;
    }
    
    private Color getFunctionColor() {
        return isDarkTheme() ? DARK_FUNCTION_COLOR : LIGHT_FUNCTION_COLOR;
    }
    
    private Color getOperatorColor() {
        return isDarkTheme() ? DARK_OPERATOR_COLOR : LIGHT_OPERATOR_COLOR;
    }
    
    private Color getIdentifierColor() {
        return isDarkTheme() ? DARK_IDENTIFIER_COLOR : LIGHT_IDENTIFIER_COLOR;
    }
    
    private Color getTypeColor() {
        return isDarkTheme() ? DARK_TYPE_COLOR : LIGHT_TYPE_COLOR;
    }
    
    private Color getNamespaceColor() {
        return isDarkTheme() ? DARK_NAMESPACE_COLOR : LIGHT_NAMESPACE_COLOR;
    }
    
    private Color getBuiltInVarColor() {
        return isDarkTheme() ? DARK_BUILT_IN_VAR_COLOR : LIGHT_BUILT_IN_VAR_COLOR;
    }
    
    // Helper method to detect if we're in a dark theme
    private boolean isDarkTheme() {
        return UIUtil.isUnderDarcula();
    }

    // Create TextAttributes with dynamic colors
    private TextAttributes createKeywordAttributes() {
        return new TextAttributes(getKeywordColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createStringAttributes() {
        return new TextAttributes(getStringColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createNumberAttributes() {
        return new TextAttributes(getNumberColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createCommentAttributes() {
        return new TextAttributes(getCommentColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createFunctionAttributes() {
        return new TextAttributes(getFunctionColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createOperatorAttributes() {
        return new TextAttributes(getOperatorColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createIdentifierAttributes() {
        return new TextAttributes(getIdentifierColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createTypeAttributes() {
        return new TextAttributes(getKeywordColor(), null, null, null, Font.BOLD);
    }
    
    private TextAttributes createNamespaceAttributes() {
        return new TextAttributes(getNamespaceColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createBuiltInVarAttributes() {
        return new TextAttributes(getBuiltInVarColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createTrueFalseAttributes() {
        return new TextAttributes(getBuiltInVarColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createNaAttributes() {
        return new TextAttributes(getBuiltInVarColor(), null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createColorConstantAttributes() {
        return new TextAttributes(COLOR_CONSTANTS, null, null, null, Font.PLAIN);
    }
    
    private TextAttributes createExitLabelAttributes() {
        return new TextAttributes(EXIT_LABEL_COLOR, null, null, null, Font.PLAIN);
    }

    // Create TextAttributesKey with custom attributes
    public static final TextAttributesKey KEYWORD = createTextAttributesKey("PINE_SCRIPT_KEYWORD");
    public static final TextAttributesKey STRING = createTextAttributesKey("PINE_SCRIPT_STRING");
    public static final TextAttributesKey NUMBER = createTextAttributesKey("PINE_SCRIPT_NUMBER");
    public static final TextAttributesKey COMMENT = createTextAttributesKey("PINE_SCRIPT_COMMENT");
    public static final TextAttributesKey FUNCTION = createTextAttributesKey("PINE_SCRIPT_FUNCTION");
    public static final TextAttributesKey OPERATOR = createTextAttributesKey("PINE_SCRIPT_OPERATOR");
    public static final TextAttributesKey IDENTIFIER = createTextAttributesKey("PINE_SCRIPT_IDENTIFIER");
    public static final TextAttributesKey TYPE = createTextAttributesKey("PINE_SCRIPT_TYPE");
    public static final TextAttributesKey NAMESPACE = createTextAttributesKey("PINE_SCRIPT_NAMESPACE");
    public static final TextAttributesKey TRUE_FALSE = createTextAttributesKey("PINE_SCRIPT_TRUE_FALSE");
    public static final TextAttributesKey NA = createTextAttributesKey("PINE_SCRIPT_NA");
    public static final TextAttributesKey COLOR_CONSTANT = createTextAttributesKey("PINE_SCRIPT_COLOR_CONSTANT");
    public static final TextAttributesKey EXIT_LABEL = createTextAttributesKey("PINE_SCRIPT_EXIT_LABEL");
    public static final TextAttributesKey BUILT_IN_VARIABLE = createTextAttributesKey("PINE_SCRIPT_BUILT_IN_VARIABLE");
    
    // Use default colors for these
    public static final TextAttributesKey PARENTHESES = createTextAttributesKey("PINE_SCRIPT_PARENTHESES");
    public static final TextAttributesKey BRACKETS = createTextAttributesKey("PINE_SCRIPT_BRACKETS");
    public static final TextAttributesKey BRACES = createTextAttributesKey("PINE_SCRIPT_BRACES");
    public static final TextAttributesKey COMMA = createTextAttributesKey("PINE_SCRIPT_COMMA");
    public static final TextAttributesKey DOT = createTextAttributesKey("PINE_SCRIPT_DOT");
    public static final TextAttributesKey SEMICOLON = createTextAttributesKey("PINE_SCRIPT_SEMICOLON");
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

        // Special case to fix string highlighting
        if (tokenType == PineScriptTokenTypes.STRING) {
            return packWithAttributes(STRING, createStringAttributes());
        }

        if (KEYWORD_TOKENS.contains(tokenType)) {
            return packWithAttributes(KEYWORD, createKeywordAttributes());
        } else if (IDENTIFIER_TOKENS.contains(tokenType)) {
            return packWithAttributes(IDENTIFIER, createIdentifierAttributes());
        } else if (COMMENT_TOKENS.contains(tokenType)) {
            return packWithAttributes(COMMENT, createCommentAttributes());
        } else if (NUMBER_TOKENS.contains(tokenType)) {
            return packWithAttributes(NUMBER, createNumberAttributes());
        } else if (OPERATOR_TOKENS.contains(tokenType)) {
            return packWithAttributes(OPERATOR, createOperatorAttributes());
        } else if (FUNCTION_TOKENS.contains(tokenType)) {
            return packWithAttributes(FUNCTION, createFunctionAttributes());
        } else if (NAMESPACE_TOKENS.contains(tokenType)) {
            return packWithAttributes(NAMESPACE, createNamespaceAttributes());
        } else if (TYPE_TOKENS.contains(tokenType)) {
            return packWithAttributes(TYPE, createTypeAttributes());
        } else if (TRUE_FALSE_TOKENS.contains(tokenType)) {
            return packWithAttributes(TRUE_FALSE, createTrueFalseAttributes());
        } else if (NA_TOKENS.contains(tokenType)) {
            return packWithAttributes(NA, createNaAttributes());
        } else if (COLOR_CONSTANT_TOKENS.contains(tokenType)) {
            return packWithAttributes(COLOR_CONSTANT, createColorConstantAttributes());
        } else if (EXIT_LABEL_TOKENS.contains(tokenType)) {
            return packWithAttributes(EXIT_LABEL, createExitLabelAttributes());
        } else if (BUILT_IN_VAR_TOKENS.contains(tokenType)) {
            return packWithAttributes(BUILT_IN_VARIABLE, createBuiltInVarAttributes());
        } else if (BAD_CHAR_TOKENS.contains(tokenType)) {
            return pack(BAD_CHARACTER);
        }

        // Handle punctuation
        if (tokenType.equals(PineScriptTokenTypes.LPAREN) || tokenType.equals(PineScriptTokenTypes.RPAREN)) {
            return packWithAttributes(PARENTHESES, createOperatorAttributes());
        } else if (tokenType.equals(PineScriptTokenTypes.LBRACKET) || tokenType.equals(PineScriptTokenTypes.RBRACKET)) {
            return packWithAttributes(BRACKETS, createOperatorAttributes());
        } else if (tokenType.equals(PineScriptTokenTypes.LBRACE) || tokenType.equals(PineScriptTokenTypes.RBRACE)) {
            return packWithAttributes(BRACES, createOperatorAttributes());
        } else if (tokenType.equals(PineScriptTokenTypes.COMMA)) {
            return packWithAttributes(COMMA, createOperatorAttributes());
        } else if (tokenType.equals(PineScriptTokenTypes.DOT)) {
            return packWithAttributes(DOT, createOperatorAttributes());
        } else if (tokenType.equals(PineScriptTokenTypes.SEMICOLON)) {
            return packWithAttributes(SEMICOLON, createOperatorAttributes());
        }

        // Default to identifier color for any unmatched tokens that might be variables
        return packWithAttributes(IDENTIFIER, createIdentifierAttributes());
    }
    
    // Helper method to create attribute pairs with dynamic colors
    private TextAttributesKey @NotNull [] packWithAttributes(TextAttributesKey key, TextAttributes attributes) {
        return new TextAttributesKey[]{TextAttributesKey.createTextAttributesKey(key.getExternalName(), attributes)};
    }
} 