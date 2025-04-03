package com.pinescript.plugin.editor;

import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.pinescript.plugin.language.PineScriptLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PineScriptColorProvider implements ElementColorProvider {
    private static final Pattern COLOR_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");
    private static final Pattern COLOR_FUNCTION_PATTERN = Pattern.compile("color\\.([a-zA-Z_][a-zA-Z0-9_]*)");
    
    // Pre-defined Pine Script colors
    private static final java.util.Map<String, Color> PREDEFINED_COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("red", new Color(255, 0, 0)),
            java.util.Map.entry("green", new Color(0, 255, 0)),
            java.util.Map.entry("blue", new Color(0, 0, 255)),
            java.util.Map.entry("black", new Color(0, 0, 0)),
            java.util.Map.entry("white", new Color(255, 255, 255)),
            java.util.Map.entry("orange", new Color(255, 165, 0)),
            java.util.Map.entry("yellow", new Color(255, 255, 0)),
            java.util.Map.entry("purple", new Color(128, 0, 128)),
            java.util.Map.entry("lime", new Color(0, 255, 0)),
            java.util.Map.entry("aqua", new Color(0, 255, 255)),
            java.util.Map.entry("gray", new Color(128, 128, 128)),
            java.util.Map.entry("silver", new Color(192, 192, 192)),
            java.util.Map.entry("maroon", new Color(128, 0, 0)),
            java.util.Map.entry("olive", new Color(128, 128, 0)),
            java.util.Map.entry("teal", new Color(0, 128, 128)),
            java.util.Map.entry("navy", new Color(0, 0, 128))
    );

    @Nullable
    @Override
    public Color getColorFrom(@NotNull PsiElement element) {
        if (!(element.getContainingFile().getLanguage() instanceof PineScriptLanguage)) {
            return null;
        }

        String text = element.getText();
        
        // Check for hex color
        Matcher hexMatcher = COLOR_PATTERN.matcher(text);
        if (hexMatcher.matches()) {
            String hexColor = hexMatcher.group(1);
            try {
                return Color.decode("#" + hexColor);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // Check for color.NAME format
        Matcher funcMatcher = COLOR_FUNCTION_PATTERN.matcher(text);
        if (funcMatcher.matches()) {
            String colorName = funcMatcher.group(1);
            return PREDEFINED_COLORS.get(colorName);
        }
        
        return null;
    }

    @Override
    public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
        // For now, we don't support modifying colors in the editor
    }
} 