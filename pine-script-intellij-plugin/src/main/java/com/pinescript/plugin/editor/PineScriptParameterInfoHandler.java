package com.pinescript.plugin.editor;

import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.pinescript.plugin.completion.PineScriptFunctionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PineScriptParameterInfoHandler implements ParameterInfoHandler<PsiElement, PineScriptFunctionData.FunctionInfo> {
    @Nullable
    public Object[] getParametersForLookup(Object item, ParameterInfoContext context) {
        return null;
    }

    @Nullable
    @Override
    public PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();
        
        // Find function call at current position
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }
        
        // This is a simplified implementation. In a real plugin, we would find the actual function call PSI element
        String functionName = findFunctionName(element);
        if (functionName != null) {
            PineScriptFunctionData.FunctionInfo[] functionInfos = PineScriptFunctionData.getFunctionInfo(functionName);
            if (functionInfos != null && functionInfos.length > 0) {
                context.setItemsToShow(functionInfos);
                return element;
            }
        }
        
        return null;
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, element.getTextOffset(), this);
    }

    @Nullable
    @Override
    public PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return context.getFile().findElementAt(context.getOffset());
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement psiElement, @NotNull UpdateParameterInfoContext context) {
        context.setCurrentParameter(getCurrentParameterIndex(psiElement, context.getOffset()));
    }

    @Override
    public void updateUI(PineScriptFunctionData.FunctionInfo functionInfo, @NotNull ParameterInfoUIContext context) {
        if (functionInfo == null) {
            context.setUIComponentEnabled(false);
            return;
        }
        
        StringBuilder builder = new StringBuilder();
        int highlightStartOffset = -1;
        int highlightEndOffset = -1;
        
        // Create parameter presentation
        int currentParam = context.getCurrentParameterIndex();
        String[] params = functionInfo.getParameters();
        
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            
            if (i == currentParam) {
                highlightStartOffset = builder.length();
            }
            
            builder.append(params[i]);
            
            if (i == currentParam) {
                highlightEndOffset = builder.length();
            }
        }
        
        context.setupUIComponentPresentation(
                builder.toString(),
                highlightStartOffset,
                highlightEndOffset,
                false,
                false,
                false,
                context.getDefaultParameterColor()
        );
    }
    
    private String findFunctionName(PsiElement element) {
        // This is a simplified implementation. In a real plugin, we would properly find the function name
        // based on the PSI structure
        PsiElement parent = element.getParent();
        if (parent != null) {
            String text = parent.getText();
            int parenIndex = text.indexOf('(');
            if (parenIndex > 0) {
                return text.substring(0, parenIndex).trim();
            }
        }
        return null;
    }
    
    private int getCurrentParameterIndex(PsiElement element, int offset) {
        // Count commas before the current position to determine parameter index
        // This is a simplified implementation
        PsiElement parent = element.getParent();
        if (parent != null) {
            String text = parent.getText();
            int commaCount = 0;
            for (int i = 0; i < Math.min(offset - parent.getTextOffset(), text.length()); i++) {
                if (text.charAt(i) == ',') {
                    commaCount++;
                }
            }
            return commaCount;
        }
        return 0;
    }
} 