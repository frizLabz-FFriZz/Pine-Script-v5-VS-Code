package com.pinescript.plugin.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.pinescript.plugin.language.PineScriptFileType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Custom auto-popup trigger implementation specifically for Pine Script
 * This explicitly loads a properties file with trigger characters
 */
public class PineScriptCompletionTrigger extends CompletionConfidence {
    private static final Logger LOG = Logger.getInstance(PineScriptCompletionTrigger.class);
    private final String autoPopupChars;
    
    public PineScriptCompletionTrigger() {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/PineScriptAutoPopup.properties")) {
            if (is != null) {
                props.load(is);
                LOG.warn("🔍 Loaded Pine Script auto-popup properties");
            } else {
                LOG.warn("🔍 Could not find Pine Script auto-popup properties file");
            }
        } catch (IOException e) {
            LOG.warn("🔍 Error loading Pine Script auto-popup properties: " + e.getMessage());
        }
        
        autoPopupChars = props.getProperty("autoPopupChars", ".,(");
        LOG.warn("🔍 PineScriptCompletionTrigger initialized with chars: " + autoPopupChars);
    }
    
    @Override
    public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement element, @NotNull PsiFile psiFile, int offset) {
        // If not a Pine Script file, let other triggers handle it
        if (!psiFile.getFileType().equals(PineScriptFileType.INSTANCE)) {
            return ThreeState.UNSURE;
        }
        
        // Check if the character at offset-1 is in our trigger chars
        if (offset > 0) {
            try {
                char c = psiFile.getText().charAt(offset - 1);
                boolean isSpecialTrigger = autoPopupChars.indexOf(c) >= 0;
                LOG.warn("🔍 shouldSkipAutopopup check at offset " + offset + 
                         " with char '" + c + "': " + (isSpecialTrigger ? "SHOW" : "DEFAULT"));
                
                // Only explicitly say NO (don't skip) for our special trigger chars
                // For all other chars, let IntelliJ decide using its default behavior
                if (isSpecialTrigger) {
                    return ThreeState.NO;  // Don't skip popup for our trigger chars
                }
            } catch (Exception e) {
                LOG.warn("🔍 Error in shouldSkipAutopopup: " + e.getMessage());
            }
        }
        
        // Default behavior - let the system decide
        return ThreeState.UNSURE;
    }
} 