package com.pinescript.plugin.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.pinescript.plugin.language.PineScriptFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Custom handler for auto-popup completion in Pine Script files.
 * This class enables autocompletion to be triggered when typing certain characters,
 * such as a dot, comma, or opening parenthesis.
 */
public class PineScriptAutoPopupCompletion extends TypedHandlerDelegate {
    private static final Logger LOG = Logger.getInstance(PineScriptAutoPopupCompletion.class);
    
    // Characters that should trigger autocompletion
    private static final char[] TRIGGER_CHARS = {'.', '(', ','};
    
    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        // Only handle Pine Script files
        if (!file.getFileType().equals(PineScriptFileType.INSTANCE)) {
            return Result.CONTINUE;
        }
        
        // Check if the typed character is in our trigger list
        for (char triggerChar : TRIGGER_CHARS) {
            if (c == triggerChar) {
                LOG.warn("Auto-popup triggered by character: " + c);
                
                // Show the completion popup
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                
                // For dot specifically, try the more specific member lookup
                if (c == '.') {
                    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
                    LOG.warn("Member lookup triggered after dot character");
                }
                
                return Result.STOP;
            }
        }
        
        return Result.CONTINUE;
    }
} 