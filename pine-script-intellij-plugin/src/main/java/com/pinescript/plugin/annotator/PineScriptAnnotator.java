package com.pinescript.plugin.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.pinescript.plugin.editor.PineScriptSyntaxHighlighter;
import com.pinescript.plugin.psi.PineScriptTokenTypes;
import org.jetbrains.annotations.NotNull;

public class PineScriptAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Annotate version tag
        if (element.getText().startsWith("//@version=")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(element)
                  .textAttributes(PineScriptSyntaxHighlighter.KEYWORD)
                  .create();
            return;
        }

        // Annotate built-in functions
        if (element.getNode().getElementType() == PineScriptTokenTypes.FUNCTION) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(element)
                  .textAttributes(PineScriptSyntaxHighlighter.FUNCTION)
                  .create();
            return;
        }

        // Annotate namespaces
        if (element.getNode().getElementType() == PineScriptTokenTypes.NAMESPACE) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(element)
                  .textAttributes(PineScriptSyntaxHighlighter.NAMESPACE)
                  .create();
            return;
        }

        // Annotate types
        if (element.getNode().getElementType() == PineScriptTokenTypes.TYPE) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(element)
                  .textAttributes(PineScriptSyntaxHighlighter.TYPE)
                  .create();
            return;
        }
    }
} 