package com.pinescript.plugin.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.pinescript.plugin.parser.PineScriptParser;
import com.pinescript.plugin.psi.PineScriptFile;
import com.pinescript.plugin.psi.PineScriptTokenTypes;
import org.jetbrains.annotations.NotNull;

public class PineScriptParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(PineScriptLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new PineScriptLexer();
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return TokenSet.create(PineScriptTokenTypes.COMMENT);
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return TokenSet.create(PineScriptTokenTypes.STRING);
    }

    @NotNull
    @Override
    public PsiParser createParser(final Project project) {
        return new PineScriptParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PineScriptFile(viewProvider);
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return PineScriptTokenTypes.Factory.createElement(node);
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return TokenSet.create(TokenType.WHITE_SPACE);
    }
} 