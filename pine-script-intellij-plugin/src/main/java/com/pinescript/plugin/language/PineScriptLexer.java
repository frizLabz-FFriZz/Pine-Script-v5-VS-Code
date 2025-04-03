package com.pinescript.plugin.language;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import com.pinescript.plugin.psi.PineScriptTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PineScriptLexer extends LexerBase {
    private CharSequence buffer;
    private int bufferEnd;
    private int currentPosition;
    private int tokenStart;
    private int tokenEnd;
    private IElementType currentToken;
    
    // Pattern for identifying tokens more robustly
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b(if|else|for|to|while|var|varip|import|export|switch|case|default|continue|break|return|type|enum|function|method|strategy|indicator|library|series|simple|const|input|and|or|not|when|overlay)\\b");
    private static final Pattern BUILT_IN_VAR_PATTERN = Pattern.compile("\\b(open|high|low|close|volume|time|hl2|hlc3|ohlc4|bar_index|barstate|title|comment|style|from_entry|stop|immediately|entry_id)\\b");
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("\\b(math|array|matrix|str|color|table|chart|strategy|syminfo|ta|request|ticker|label|plot|plotshape|plotchar|bar_index|plotbar|hline|fill)\\b");
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\b(int|float|bool|string|color|label|line|box|table)\\b");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("\\b(true|false)\\b");
    private static final Pattern NA_PATTERN = Pattern.compile("\\bna\\b");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(");
    private static final Pattern COLOR_PATTERN = Pattern.compile("\\bcolor\\.[a-zA-Z_][a-zA-Z0-9_]*\\b");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6,8}\\b");
    private static final Pattern CONSTANT_PATTERN = Pattern.compile("\\b[A-Z_][A-Z0-9_]*\\b");
    
    // Special token sets
    private static final Set<String> KEYWORDS = new HashSet<>();
    private static final Set<String> CONTROL_KEYWORDS = new HashSet<>(); // if, else, for, etc.
    private static final Set<String> DECLARATION_KEYWORDS = new HashSet<>(); // var, varip, const, etc.
    private static final Set<String> NAMESPACES = new HashSet<>();
    private static final Set<String> TYPES = new HashSet<>();
    private static final Set<String> BOOLEAN_VALUES = new HashSet<>();
    private static final Set<String> NA_VALUES = new HashSet<>();
    private static final Set<String> COLOR_CONSTANTS = new HashSet<>();
    private static final Set<String> EXIT_LABELS = new HashSet<>();
    private static final Set<String> BUILT_IN_VARS = new HashSet<>();
    private static final Set<String> OPERATORS = new HashSet<>();
    private static final Set<String> BUILT_IN_CONSTANTS = new HashSet<>(); // Additional set for built-in constants like color.red
    
    static {
        // Keywords
        KEYWORDS.add("if"); KEYWORDS.add("else"); KEYWORDS.add("for"); KEYWORDS.add("to");
        KEYWORDS.add("while"); KEYWORDS.add("var"); KEYWORDS.add("varip"); KEYWORDS.add("import");
        KEYWORDS.add("export"); KEYWORDS.add("switch"); KEYWORDS.add("case"); KEYWORDS.add("default");
        KEYWORDS.add("continue"); KEYWORDS.add("break"); KEYWORDS.add("return"); KEYWORDS.add("type");
        KEYWORDS.add("enum"); KEYWORDS.add("function"); KEYWORDS.add("method"); KEYWORDS.add("strategy");
        KEYWORDS.add("indicator"); KEYWORDS.add("library"); KEYWORDS.add("series"); KEYWORDS.add("simple");
        KEYWORDS.add("const"); KEYWORDS.add("input"); KEYWORDS.add("and"); KEYWORDS.add("or"); KEYWORDS.add("not");
        KEYWORDS.add("=>"); KEYWORDS.add("when"); KEYWORDS.add("overlay");
        
        // Control keywords
        CONTROL_KEYWORDS.add("if"); CONTROL_KEYWORDS.add("else"); CONTROL_KEYWORDS.add("for");
        CONTROL_KEYWORDS.add("while"); CONTROL_KEYWORDS.add("switch"); CONTROL_KEYWORDS.add("case");
        CONTROL_KEYWORDS.add("default"); CONTROL_KEYWORDS.add("continue"); CONTROL_KEYWORDS.add("break");
        CONTROL_KEYWORDS.add("return"); CONTROL_KEYWORDS.add("to"); CONTROL_KEYWORDS.add("by"); 
        CONTROL_KEYWORDS.add("when");
        
        // Declaration keywords
        DECLARATION_KEYWORDS.add("var"); DECLARATION_KEYWORDS.add("varip"); DECLARATION_KEYWORDS.add("const");
        DECLARATION_KEYWORDS.add("function"); DECLARATION_KEYWORDS.add("method"); DECLARATION_KEYWORDS.add("type");
        DECLARATION_KEYWORDS.add("enum"); DECLARATION_KEYWORDS.add("strategy"); DECLARATION_KEYWORDS.add("indicator");
        DECLARATION_KEYWORDS.add("library"); DECLARATION_KEYWORDS.add("series"); DECLARATION_KEYWORDS.add("simple");
        DECLARATION_KEYWORDS.add("input"); DECLARATION_KEYWORDS.add("export"); DECLARATION_KEYWORDS.add("import");
        
        // Built-in variables
        BUILT_IN_VARS.add("open"); BUILT_IN_VARS.add("high"); BUILT_IN_VARS.add("low"); BUILT_IN_VARS.add("close");
        BUILT_IN_VARS.add("volume"); BUILT_IN_VARS.add("time"); BUILT_IN_VARS.add("hl2"); BUILT_IN_VARS.add("hlc3");
        BUILT_IN_VARS.add("ohlc4"); BUILT_IN_VARS.add("bar_index"); BUILT_IN_VARS.add("barstate");
        BUILT_IN_VARS.add("title"); BUILT_IN_VARS.add("comment"); BUILT_IN_VARS.add("style"); BUILT_IN_VARS.add("from_entry");
        BUILT_IN_VARS.add("stop"); BUILT_IN_VARS.add("immediately"); BUILT_IN_VARS.add("entry_id");
        BUILT_IN_VARS.add("entryPrice"); // Add common variable names from the example
        BUILT_IN_VARS.add("trailActive"); BUILT_IN_VARS.add("extremePrice"); BUILT_IN_VARS.add("trailStop");
        BUILT_IN_VARS.add("trailingStopLossDelta"); BUILT_IN_VARS.add("stopLossActivationDelta");
        
        // Namespaces
        NAMESPACES.add("math"); NAMESPACES.add("array"); NAMESPACES.add("matrix"); NAMESPACES.add("str");
        NAMESPACES.add("color"); NAMESPACES.add("table"); NAMESPACES.add("chart"); NAMESPACES.add("strategy");
        NAMESPACES.add("syminfo"); NAMESPACES.add("ta"); NAMESPACES.add("request"); NAMESPACES.add("ticker");
        NAMESPACES.add("label"); NAMESPACES.add("plot"); NAMESPACES.add("plotshape"); NAMESPACES.add("plotchar");
        NAMESPACES.add("bar_index"); NAMESPACES.add("plotbar"); NAMESPACES.add("hline"); NAMESPACES.add("fill");
        
        // Types
        TYPES.add("int"); TYPES.add("float"); TYPES.add("bool"); TYPES.add("string");
        TYPES.add("color"); TYPES.add("label"); TYPES.add("line"); TYPES.add("box");
        TYPES.add("table");
        
        // Boolean values
        BOOLEAN_VALUES.add("true"); BOOLEAN_VALUES.add("false");
        
        // NA values
        NA_VALUES.add("na");
        
        // Color constants
        COLOR_CONSTANTS.add("color.red"); COLOR_CONSTANTS.add("color.green"); COLOR_CONSTANTS.add("color.blue");
        COLOR_CONSTANTS.add("color.black"); COLOR_CONSTANTS.add("color.white"); COLOR_CONSTANTS.add("color.yellow");
        COLOR_CONSTANTS.add("color.purple"); COLOR_CONSTANTS.add("color.orange"); COLOR_CONSTANTS.add("color.gray");
        COLOR_CONSTANTS.add("color.lime"); COLOR_CONSTANTS.add("color.aqua"); COLOR_CONSTANTS.add("color.silver");
        COLOR_CONSTANTS.add("color.maroon"); COLOR_CONSTANTS.add("color.fuchsia"); COLOR_CONSTANTS.add("color.navy");
        COLOR_CONSTANTS.add("color.olive"); COLOR_CONSTANTS.add("color.teal");
        
        // Additional built-in constants
        BUILT_IN_CONSTANTS.add("barmerge.lookahead_on"); BUILT_IN_CONSTANTS.add("barmerge.lookahead_off");
        BUILT_IN_CONSTANTS.add("barmerge.gaps_on"); BUILT_IN_CONSTANTS.add("barmerge.gaps_off");
        BUILT_IN_CONSTANTS.add("barstate.isconfirmed"); BUILT_IN_CONSTANTS.add("barstate.isrealtime");
        BUILT_IN_CONSTANTS.add("barstate.islast"); BUILT_IN_CONSTANTS.add("barstate.ishistory");
        BUILT_IN_CONSTANTS.add("barstate.isfirst"); BUILT_IN_CONSTANTS.add("barstate.isnew");
        
        // Exit labels
        EXIT_LABELS.add("Long TS Exit"); EXIT_LABELS.add("Short TS Exit");
        
        // Operators
        OPERATORS.add("+"); OPERATORS.add("-"); OPERATORS.add("*"); OPERATORS.add("/");
        OPERATORS.add("%"); OPERATORS.add("=="); OPERATORS.add("!="); OPERATORS.add("<");
        OPERATORS.add(">"); OPERATORS.add("<="); OPERATORS.add(">="); OPERATORS.add("=");
        OPERATORS.add("+="); OPERATORS.add("-="); OPERATORS.add("*="); OPERATORS.add("/=");
        OPERATORS.add("?"); OPERATORS.add(":"); OPERATORS.add("=>"); OPERATORS.add(".");
        OPERATORS.add("!"); OPERATORS.add("&&"); OPERATORS.add("||"); OPERATORS.add("++");
        OPERATORS.add("--"); OPERATORS.add("&"); OPERATORS.add("|"); OPERATORS.add("^");
        OPERATORS.add("~"); OPERATORS.add("<<"); OPERATORS.add(">>"); OPERATORS.add(">>>");
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.currentPosition = startOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.currentToken = null;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return currentToken;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        if (currentPosition >= bufferEnd) {
            currentToken = null;
            tokenEnd = currentPosition;
            return;
        }

        tokenStart = currentPosition;
        tokenEnd = currentPosition;
        char c = buffer.charAt(currentPosition++);

        if (Character.isWhitespace(c)) {
            while (currentPosition < bufferEnd && Character.isWhitespace(buffer.charAt(currentPosition))) {
                currentPosition++;
            }
            currentToken = TokenType.WHITE_SPACE;
        } else if (c == '/' && currentPosition < bufferEnd && buffer.charAt(currentPosition) == '/') {
            while (currentPosition < bufferEnd && buffer.charAt(currentPosition) != '\n') {
                currentPosition++;
            }
            currentToken = PineScriptTokenTypes.COMMENT;
        } else if (c == '"' || c == '\'') {
            char quote = c;
            boolean escaped = false;
            while (currentPosition < bufferEnd) {
                char next = buffer.charAt(currentPosition);
                if (next == '\\' && !escaped) {
                    escaped = true;
                } else if (next == quote && !escaped) {
                    currentPosition++;
                    break;
                } else {
                    escaped = false;
                }
                currentPosition++;
            }
            
            // Check if this is an exit label string
            String tokenText = buffer.subSequence(tokenStart, currentPosition).toString();
            if (isExitLabel(tokenText)) {
                currentToken = PineScriptTokenTypes.EXIT_LABEL;
            } else {
                currentToken = PineScriptTokenTypes.STRING;
            }
        } else if (c == '#') {
            // Try to match hex color
            int startPos = currentPosition - 1;  // Include the '#'
            StringBuilder sb = new StringBuilder("#");
            
            while (currentPosition < bufferEnd && 
                  (Character.isDigit(buffer.charAt(currentPosition)) || 
                   (buffer.charAt(currentPosition) >= 'a' && buffer.charAt(currentPosition) <= 'f') ||
                   (buffer.charAt(currentPosition) >= 'A' && buffer.charAt(currentPosition) <= 'F'))) {
                sb.append(buffer.charAt(currentPosition));
                currentPosition++;
            }
            
            String hexValue = sb.toString();
            if (hexValue.length() >= 7 && hexValue.length() <= 9) {  // #RRGGBB or #RRGGBBAA
                currentToken = PineScriptTokenTypes.COLOR_CONSTANT;
            } else {
                // Not a valid hex color, treat as bad character
                currentToken = TokenType.BAD_CHARACTER;
            }
        } else if (Character.isDigit(c) || (c == '.' && currentPosition < bufferEnd && Character.isDigit(buffer.charAt(currentPosition)))) {
            boolean hasDecimal = c == '.';
            while (currentPosition < bufferEnd) {
                char next = buffer.charAt(currentPosition);
                if (Character.isDigit(next)) {
                    currentPosition++;
                } else if (next == '.' && !hasDecimal) {
                    hasDecimal = true;
                    currentPosition++;
                } else if ((next == 'e' || next == 'E') && 
                          currentPosition + 1 < bufferEnd && 
                          (Character.isDigit(buffer.charAt(currentPosition + 1)) || 
                           buffer.charAt(currentPosition + 1) == '+' || 
                           buffer.charAt(currentPosition + 1) == '-')) {
                    // Handle scientific notation like 1e10, 1e+10, 1e-10
                    currentPosition++;  // Consume 'e' or 'E'
                    if (buffer.charAt(currentPosition) == '+' || buffer.charAt(currentPosition) == '-') {
                        currentPosition++;  // Consume '+' or '-'
                    }
                    // Now consume the exponent
                    while (currentPosition < bufferEnd && Character.isDigit(buffer.charAt(currentPosition))) {
                        currentPosition++;
                    }
                    break;
                } else {
                    break;
                }
            }
            currentToken = PineScriptTokenTypes.NUMBER;
        } else if (isOperator(c)) {
            // Handle multi-character operators
            if (currentPosition < bufferEnd) {
                char next = buffer.charAt(currentPosition);
                if ((c == '=' && next == '=') || // ==
                    (c == '!' && next == '=') || // !=
                    (c == '<' && next == '=') || // <=
                    (c == '>' && next == '=') || // >=
                    (c == '+' && next == '=') || // +=
                    (c == '-' && next == '=') || // -=
                    (c == '*' && next == '=') || // *=
                    (c == '/' && next == '=') || // /=
                    (c == '&' && next == '&') || // &&
                    (c == '|' && next == '|') || // ||
                    (c == '+' && next == '+') || // ++
                    (c == '-' && next == '-') || // --
                    (c == '<' && next == '<') || // <<
                    (c == '>' && next == '>') || // >>
                    (c == '=' && next == '>')) { // =>
                    currentPosition++;
                    
                    // Handle >>> operator (right shift with zero fill)
                    if ((c == '>' && next == '>') && currentPosition < bufferEnd && buffer.charAt(currentPosition) == '>') {
                        currentPosition++;
                    }
                }
            }
            currentToken = PineScriptTokenTypes.OPERATOR;
        } else if (isPunctuation(c)) {
            // Punctuation tokens
            if (c == '(') {
                currentToken = PineScriptTokenTypes.LPAREN;
            } else if (c == ')') {
                currentToken = PineScriptTokenTypes.RPAREN;
            } else if (c == '[') {
                currentToken = PineScriptTokenTypes.LBRACKET;
            } else if (c == ']') {
                currentToken = PineScriptTokenTypes.RBRACKET;
            } else if (c == '{') {
                currentToken = PineScriptTokenTypes.LBRACE;
            } else if (c == '}') {
                currentToken = PineScriptTokenTypes.RBRACE;
            } else if (c == ',') {
                currentToken = PineScriptTokenTypes.COMMA;
            } else if (c == '.') {
                currentToken = PineScriptTokenTypes.DOT;
            } else if (c == ';') {
                currentToken = PineScriptTokenTypes.SEMICOLON;
            } else {
                currentToken = TokenType.BAD_CHARACTER;
            }
        } else if (Character.isLetter(c) || c == '_') {
            // Scan ahead to find the end of this token
            while (currentPosition < bufferEnd && 
                  (Character.isLetterOrDigit(buffer.charAt(currentPosition)) || 
                   buffer.charAt(currentPosition) == '_')) {
                currentPosition++;
            }
            
            // Check for namespace.method pattern
            boolean hasDot = false;
            int dotPosition = -1;
            if (currentPosition < bufferEnd && buffer.charAt(currentPosition) == '.') {
                hasDot = true;
                dotPosition = currentPosition;
                currentPosition++;  // Consume the dot
                
                // Continue consuming the method/property name
                while (currentPosition < bufferEnd && 
                      (Character.isLetterOrDigit(buffer.charAt(currentPosition)) || 
                       buffer.charAt(currentPosition) == '_')) {
                    currentPosition++;
                }
            }
            
            // Look ahead to see if this is followed by a parenthesis (indicating a function call)
            boolean isFunction = false;
            if (currentPosition < bufferEnd) {
                // Skip whitespace
                int tempPos = currentPosition;
                while (tempPos < bufferEnd && Character.isWhitespace(buffer.charAt(tempPos))) {
                    tempPos++;
                }
                
                // Check if there's an opening parenthesis
                if (tempPos < bufferEnd && buffer.charAt(tempPos) == '(') {
                    isFunction = true;
                }
            }
            
            String token = buffer.subSequence(tokenStart, currentPosition).toString();
            
            // Determine token type based on the content and context
            if (hasDot) {
                String namespace = buffer.subSequence(tokenStart, dotPosition).toString();
                
                if (namespace.equals("color")) {
                    // Color constants like color.red
                    currentToken = PineScriptTokenTypes.COLOR_CONSTANT;
                } else if (isBuiltInConstant(token)) {
                    // Other built-in constants like barmerge.lookahead_on
                    currentToken = PineScriptTokenTypes.BUILT_IN_VARIABLE;
                } else if (isNamespace(namespace)) {
                    if (isFunction) {
                        // Namespace function calls like math.sin()
                        currentToken = PineScriptTokenTypes.FUNCTION;
                    } else {
                        // Namespace property access
                        currentToken = PineScriptTokenTypes.NAMESPACE;
                    }
                } else {
                    // Other dotted expressions
                    if (isFunction) {
                        currentToken = PineScriptTokenTypes.FUNCTION;
                    } else {
                        currentToken = PineScriptTokenTypes.IDENTIFIER;
                    }
                }
            } else {
                // Simple tokens (not dotted)
                if (isControlKeyword(token)) {
                    // Control flow keywords (if, else, for, etc.)
                    currentToken = PineScriptTokenTypes.KEYWORD;
                } else if (isDeclarationKeyword(token)) {
                    // Declaration keywords (var, function, etc.)
                    currentToken = PineScriptTokenTypes.KEYWORD;
                } else if (isType(token)) {
                    currentToken = PineScriptTokenTypes.TYPE;
                } else if (isBoolean(token)) {
                    currentToken = PineScriptTokenTypes.TRUE_FALSE;
                } else if (isNaValue(token)) {
                    currentToken = PineScriptTokenTypes.NA;
                } else if (isBuiltInVar(token)) {
                    currentToken = PineScriptTokenTypes.BUILT_IN_VARIABLE;
                } else if (isFunction) {
                    currentToken = PineScriptTokenTypes.FUNCTION;
                } else if (isNamespace(token)) {
                    currentToken = PineScriptTokenTypes.NAMESPACE;
                } else if (isConstant(token)) {
                    // Constants like MAX_VALUE
                    currentToken = PineScriptTokenTypes.BUILT_IN_VARIABLE;
                } else {
                    currentToken = PineScriptTokenTypes.IDENTIFIER;
                }
            }
        } else {
            // Unrecognized character
            currentToken = TokenType.BAD_CHARACTER;
        }
        
        tokenEnd = currentPosition;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    private boolean isKeyword(String word) {
        return KEYWORDS.contains(word) || KEYWORD_PATTERN.matcher(word).matches();
    }
    
    private boolean isControlKeyword(String word) {
        return CONTROL_KEYWORDS.contains(word);
    }
    
    private boolean isDeclarationKeyword(String word) {
        return DECLARATION_KEYWORDS.contains(word);
    }

    private boolean isNamespace(String word) {
        return NAMESPACES.contains(word) || NAMESPACE_PATTERN.matcher(word).matches();
    }

    private boolean isType(String word) {
        return TYPES.contains(word) || TYPE_PATTERN.matcher(word).matches();
    }

    private boolean isBoolean(String word) {
        return BOOLEAN_VALUES.contains(word) || BOOLEAN_PATTERN.matcher(word).matches();
    }

    private boolean isNaValue(String word) {
        return NA_VALUES.contains(word) || NA_PATTERN.matcher(word).matches();
    }

    private boolean isColorConstant(String word) {
        return COLOR_CONSTANTS.contains(word) || COLOR_PATTERN.matcher(word).matches();
    }
    
    private boolean isHexColor(String word) {
        return HEX_COLOR_PATTERN.matcher(word).matches();
    }
    
    private boolean isConstant(String word) {
        return CONSTANT_PATTERN.matcher(word).matches(); // All caps
    }
    
    private boolean isBuiltInConstant(String word) {
        return BUILT_IN_CONSTANTS.contains(word);
    }

    private boolean isExitLabel(String word) {
        // Strip quotes from the string
        if (word.length() >= 2 && (word.startsWith("\"") || word.startsWith("'")) && 
            (word.endsWith("\"") || word.endsWith("'"))) {
            word = word.substring(1, word.length() - 1);
        }
        return EXIT_LABELS.contains(word);
    }

    private boolean isOperator(char c) {
        String s = String.valueOf(c);
        return OPERATORS.contains(s) ||
               c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
               c == '=' || c == '!' || c == '<' || c == '>' || c == '&' ||
               c == '|' || c == '^' || c == '~' || c == '?';
    }

    private boolean isPunctuation(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || 
               c == '}' || c == ',' || c == '.' || c == ';';
    }

    private boolean isBuiltInVar(String word) {
        return BUILT_IN_VARS.contains(word) || BUILT_IN_VAR_PATTERN.matcher(word).matches();
    }
} 