package com.pinescript.plugin.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
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
                // Get context for better logging
                Document document = editor.getDocument();
                int offset = editor.getCaretModel().getOffset();
                String context = "";
                
                if (offset > 10) {
                    context = document.getText().substring(offset - 10, offset);
                } else if (offset > 0) {
                    context = document.getText().substring(0, offset);
                }
                
                LOG.warn("🔍 COMPLETION HANDLER: Auto-popup triggered by character: '" + c + "' at offset " + offset + ", context: \"" + context + "\"");
                
                // For dot specifically, try multiple approaches to trigger completion
                if (c == '.') {
                    LOG.warn("🔍 COMPLETION HANDLER: DOT TYPED - Using multiple approaches to trigger completion");
                    
                    // Ensure document is committed first
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                    
                    // APPROACH 1: Use AutoPopupController for member lookup
                    try {
                        LOG.warn("🔍 COMPLETION HANDLER: Approach 1 - Using AutoPopupController.autoPopupMemberLookup");
                        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
                    } catch (Exception e) {
                        LOG.warn("🔍 COMPLETION HANDLER: Exception in approach 1: " + e.getMessage(), e);
                    }
                    
                    // APPROACH 2: Fallback to standard AutoPopupController
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            LOG.warn("🔍 COMPLETION HANDLER: Approach 2 - Using basic AutoPopupController.scheduleAutoPopup");
                            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                        } catch (Exception e) {
                            LOG.warn("🔍 COMPLETION HANDLER: Exception in approach 2: " + e.getMessage(), e);
                        }
                    });
                    
                    // APPROACH 3: Extreme fallback - force a modification and then completion
                    if (project.isOpen() && !project.isDisposed() && !editor.isDisposed()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                // This is a last resort, causing a dummy change to force completion systems
                                LOG.warn("🔍 COMPLETION HANDLER: Approach 3 - Forcing completion via dummy text change");
                                document.addDocumentListener(new DocumentListener() {
                                    @Override
                                    public void documentChanged(@NotNull DocumentEvent event) {
                                        document.removeDocumentListener(this);
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                                        });
                                    }
                                });
                                
                                // We need to cause a document change that won't affect code
                                ApplicationManager.getApplication().runWriteAction(() -> {
                                    EditorModificationUtil.insertStringAtCaret(editor, "");
                                });
                            } catch (Exception e) {
                                LOG.warn("🔍 COMPLETION HANDLER: Exception in approach 3: " + e.getMessage(), e);
                            }
                        });
                    }
                } else {
                    // For other characters, just use the standard approach
                    AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                }
                
                return Result.STOP;
            }
        }
        
        return Result.CONTINUE;
    }
} 