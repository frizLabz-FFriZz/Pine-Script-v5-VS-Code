package com.pinescript.plugin.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.pinescript.plugin.language.PineScriptIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class PineScriptColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Keywords", PineScriptSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("Identifiers", PineScriptSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Comments", PineScriptSyntaxHighlighter.COMMENT),
            new AttributesDescriptor("Strings", PineScriptSyntaxHighlighter.STRING),
            new AttributesDescriptor("Numbers", PineScriptSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Operators", PineScriptSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Parentheses", PineScriptSyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Brackets", PineScriptSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Braces", PineScriptSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Commas", PineScriptSyntaxHighlighter.COMMA),
            new AttributesDescriptor("Dots", PineScriptSyntaxHighlighter.DOT),
            new AttributesDescriptor("Semicolons", PineScriptSyntaxHighlighter.SEMICOLON),
            new AttributesDescriptor("Functions", PineScriptSyntaxHighlighter.FUNCTION),
            new AttributesDescriptor("Namespaces", PineScriptSyntaxHighlighter.NAMESPACE),
            new AttributesDescriptor("Types", PineScriptSyntaxHighlighter.TYPE),
            new AttributesDescriptor("Boolean Values", PineScriptSyntaxHighlighter.TRUE_FALSE),
            new AttributesDescriptor("NA Values", PineScriptSyntaxHighlighter.NA),
            new AttributesDescriptor("Color Constants", PineScriptSyntaxHighlighter.COLOR_CONSTANT),
            new AttributesDescriptor("Exit Labels", PineScriptSyntaxHighlighter.EXIT_LABEL),
            new AttributesDescriptor("Built-in Variables", PineScriptSyntaxHighlighter.BUILT_IN_VARIABLE)
    };

    @Nullable
    @Override
    public Icon getIcon() {
        return PineScriptIcons.FILE;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new PineScriptSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return """
               // This is a comment
               //@version=5
               indicator("My Script", overlay=true)
               
               var float myVar = 3.14
               myValue = na
               isTrue = true
               isFalse = false
               
               myFunction(param1, param2) =>
                   result = math.max(param1, param2)
                   result
               
               if (ta.crossover(close, open))
                   label.new(bar_index, high, text="Crossover", color=color.green)
               
               plot(close, color=color.blue, title="Close")
               plot(open, color=color.red, title="Open")
               
               if (low <= trailStop)
                   strategy.exit("Long TS Exit", from_entry=entry_id, stop=trailStop)
               
               if (high >= trailStop)
                   strategy.exit("Short TS Exit", from_entry=entry_id, stop=trailStop)
                   
               // Demonstrate built-in variables
               alertCondition(close > open, title="Price Up", message="Price moved up")
               """;
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Pine Script";
    }
} 