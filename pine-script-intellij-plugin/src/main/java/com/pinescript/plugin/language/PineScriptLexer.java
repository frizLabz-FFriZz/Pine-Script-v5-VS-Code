package com.pinescript.plugin.language;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import com.pinescript.plugin.psi.PineScriptTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PineScriptLexer extends LexerBase {
    private CharSequence buffer;
    private int bufferEnd;
    private int currentPosition;
    private int tokenStart;
    private int tokenEnd;
    private IElementType currentToken;

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
        if (currentToken == null) {
            advance();
        }
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
            return;
        }

        tokenStart = currentPosition;
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
            currentToken = PineScriptTokenTypes.STRING;
        } else if (Character.isDigit(c)) {
            while (currentPosition < bufferEnd && (Character.isDigit(buffer.charAt(currentPosition)) || buffer.charAt(currentPosition) == '.')) {
                currentPosition++;
            }
            currentToken = PineScriptTokenTypes.NUMBER;
        } else if (Character.isLetter(c) || c == '_') {
            while (currentPosition < bufferEnd && (Character.isLetterOrDigit(buffer.charAt(currentPosition)) || buffer.charAt(currentPosition) == '_')) {
                currentPosition++;
            }
            String word = buffer.subSequence(tokenStart, currentPosition).toString();
            if (isKeyword(word)) {
                currentToken = PineScriptTokenTypes.KEYWORD;
            } else if (isNamespace(word)) {
                currentToken = PineScriptTokenTypes.NAMESPACE;
            } else if (isType(word)) {
                currentToken = PineScriptTokenTypes.TYPE;
            } else if (currentPosition < bufferEnd && buffer.charAt(currentPosition) == '(') {
                currentToken = PineScriptTokenTypes.FUNCTION;
            } else {
                currentToken = PineScriptTokenTypes.IDENTIFIER;
            }
        } else if (isOperator(c)) {
            while (currentPosition < bufferEnd && isOperator(buffer.charAt(currentPosition))) {
                currentPosition++;
            }
            currentToken = PineScriptTokenTypes.OPERATOR;
        } else if (isPunctuation(c)) {
            currentToken = PineScriptTokenTypes.OPERATOR;
        } else {
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
        return word.equals("if") || word.equals("else") || word.equals("for") || word.equals("to") ||
               word.equals("while") || word.equals("var") || word.equals("varip") || word.equals("import") ||
               word.equals("export") || word.equals("switch") || word.equals("case") || word.equals("default") ||
               word.equals("continue") || word.equals("break") || word.equals("return") || word.equals("type") ||
               word.equals("enum") || word.equals("function") || word.equals("method") || word.equals("strategy") ||
               word.equals("indicator") || word.equals("library") || word.equals("true") || word.equals("false") ||
               word.equals("na") || word.equals("series") || word.equals("simple") || word.equals("const") ||
               word.equals("input");
    }

    private boolean isNamespace(String word) {
        return word.equals("math") || word.equals("array") || word.equals("matrix") || word.equals("str") ||
               word.equals("color") || word.equals("table") || word.equals("chart") || word.equals("strategy") ||
               word.equals("syminfo") || word.equals("ta") || word.equals("request") || word.equals("ticker");
    }

    private boolean isType(String word) {
        return word.equals("int") || word.equals("float") || word.equals("bool") || word.equals("string") ||
               word.equals("color") || word.equals("label") || word.equals("line") || word.equals("box") ||
               word.equals("table");
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '=' || c == '!' ||
               c == '<' || c == '>' || c == '&' || c == '|' || c == '?' || c == ':' || c == '.' ||
               c == '-';
    }

    private boolean isPunctuation(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == ',';
    }
} 