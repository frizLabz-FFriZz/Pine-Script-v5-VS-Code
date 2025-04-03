package com.pinescript.plugin.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PineScriptPsiElement extends ASTWrapperPsiElement {
    public PineScriptPsiElement(@NotNull ASTNode node) {
        super(node);
    }
} 