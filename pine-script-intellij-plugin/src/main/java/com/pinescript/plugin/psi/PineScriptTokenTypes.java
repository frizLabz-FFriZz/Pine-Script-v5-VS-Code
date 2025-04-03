package com.pinescript.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.pinescript.plugin.language.PineScriptLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface PineScriptTokenTypes {
    // Token types for Pine Script
    IElementType COMMENT = new PineScriptTokenType("COMMENT");
    IElementType STRING = new PineScriptTokenType("STRING");
    IElementType NUMBER = new PineScriptTokenType("NUMBER");
    IElementType IDENTIFIER = new PineScriptTokenType("IDENTIFIER");
    
    // Keywords
    IElementType KEYWORD = new PineScriptTokenType("KEYWORD");
    IElementType FUNCTION = new PineScriptTokenType("FUNCTION");
    IElementType BUILT_IN_VARIABLE = new PineScriptTokenType("BUILT_IN_VARIABLE");
    IElementType OPERATOR = new PineScriptTokenType("OPERATOR");
    IElementType TYPE = new PineScriptTokenType("TYPE");
    IElementType METHOD = new PineScriptTokenType("METHOD");
    IElementType NAMESPACE = new PineScriptTokenType("NAMESPACE");
    IElementType ANNOTATION = new PineScriptTokenType("ANNOTATION");
    
    // Special tokens for TradingView-like highlighting
    IElementType TRUE_FALSE = new PineScriptTokenType("TRUE_FALSE");
    IElementType NA = new PineScriptTokenType("NA");
    IElementType COLOR_CONSTANT = new PineScriptTokenType("COLOR_CONSTANT");
    IElementType EXIT_LABEL = new PineScriptTokenType("EXIT_LABEL");
    
    // Punctuation
    IElementType DOT = new PineScriptTokenType("DOT");
    IElementType COMMA = new PineScriptTokenType("COMMA");
    IElementType SEMICOLON = new PineScriptTokenType("SEMICOLON");
    IElementType LPAREN = new PineScriptTokenType("LPAREN");
    IElementType RPAREN = new PineScriptTokenType("RPAREN");
    IElementType LBRACKET = new PineScriptTokenType("LBRACKET");
    IElementType RBRACKET = new PineScriptTokenType("RBRACKET");
    IElementType LBRACE = new PineScriptTokenType("LBRACE");
    IElementType RBRACE = new PineScriptTokenType("RBRACE");
    
    class Factory {
        public static PsiElement createElement(ASTNode node) {
            return new PineScriptPsiElement(node);
        }
    }
}

class PineScriptTokenType extends IElementType {
    public PineScriptTokenType(@NotNull @NonNls String debugName) {
        super(debugName, PineScriptLanguage.INSTANCE);
    }
}

class PineScriptElementType extends IElementType {
    public PineScriptElementType(@NotNull @NonNls String debugName) {
        super(debugName, PineScriptLanguage.INSTANCE);
    }
} 