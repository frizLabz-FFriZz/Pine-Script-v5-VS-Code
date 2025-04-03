package com.pinescript.plugin.editor;

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class PineScriptHighlightingPassFactoryRegistrar implements TextEditorHighlightingPassFactoryRegistrar {
    @Override
    public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
        PineScriptHighlightingPassFactory factory = new PineScriptHighlightingPassFactory();
        factory.registerHighlightingPassFactory(registrar);
    }
} 