package com.pinescript.plugin.editor;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.pinescript.plugin.language.PineScriptFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PineScriptHighlightingPassFactory implements TextEditorHighlightingPassFactory {
    private static final Key<Long> LAST_HIGHLIGHTING_TIMESTAMP = Key.create("PINE_SCRIPT_LAST_HIGHLIGHTING_TIMESTAMP");
    private static final MergingUpdateQueue HIGHLIGHTING_QUEUE = new MergingUpdateQueue("PineScriptHighlighting", 500, true, null);
    
    public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar) {
        registrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL}, false, -1);
    }

    @Nullable
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        // Only process Pine Script files
        if (file.getFileType() != PineScriptFileType.INSTANCE) {
            return null;
        }

        // Get current time
        long currentTime = System.currentTimeMillis();
        
        // Get last highlighting timestamp
        Long lastTimestamp = editor.getUserData(LAST_HIGHLIGHTING_TIMESTAMP);
        
        // If no highlighting in the last second, force a rehighlight
        if (lastTimestamp == null || currentTime - lastTimestamp > 500) {
            editor.putUserData(LAST_HIGHLIGHTING_TIMESTAMP, currentTime);
            return new PineScriptSyntaxHighlightingPass(file.getProject(), editor, file);
        }
        
        return null;
    }
    
    private static class PineScriptSyntaxHighlightingPass extends TextEditorHighlightingPass {
        private final Editor editor;
        private final PsiFile file;
        
        protected PineScriptSyntaxHighlightingPass(Project project, Editor editor, PsiFile file) {
            super(project, editor.getDocument(), true);
            this.editor = editor;
            this.file = file;
        }
        
        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
            // Schedule highlighting refresh with slight delay to avoid too frequent updates
            HIGHLIGHTING_QUEUE.queue(new Update("rehighlight") {
                @Override
                public void run() {
                    if (!editor.isDisposed()) {
                        // Force refresh of editor highlighting by accessing color scheme attributes
                        // This triggers re-highlighting with our custom syntax highlighter
                        editor.getColorsScheme().getAttributes(PineScriptSyntaxHighlighter.KEYWORD);
                        editor.getColorsScheme().getAttributes(PineScriptSyntaxHighlighter.NAMESPACE);
                        editor.getColorsScheme().getAttributes(PineScriptSyntaxHighlighter.FUNCTION);
                        editor.getColorsScheme().getAttributes(PineScriptSyntaxHighlighter.BUILT_IN_VARIABLE);
                        editor.getColorsScheme().getAttributes(PineScriptSyntaxHighlighter.COLOR_CONSTANT);
                        
                        // Request editor component repaint
                        editor.getComponent().repaint();
                    }
                }
                
                @Override
                public boolean canEat(Update update) {
                    return true; // Allow merging of updates
                }
            });
        }
        
        @Override
        public void doApplyInformationToEditor() {
            // Force a complete rehighlight
            if (!editor.isDisposed()) {
                // Ensure proper editor context is available
                editor.getComponent().repaint();
                
                // If the document is large, only repaint visible area
                if (editor.getDocument().getTextLength() > 10000) {
                    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                }
            }
        }
    }
} 