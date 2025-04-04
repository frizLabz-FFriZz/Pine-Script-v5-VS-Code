package com.pinescript.plugin.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.pinescript.plugin.completion.handlers.SmartInsertHandler;
import com.pinescript.plugin.language.PineScriptLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PineScriptCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(PineScriptCompletionContributor.class);
    private static final Map<String, String[]> NAMESPACE_METHODS = initNamespaceMethods();
    private static final Map<String, Map<String, String>> FUNCTION_PARAMETERS = initFunctionParameters();
    
    private static final String[] KEYWORDS = {
            "if", "else", "for", "to", "while", "var", "varip", "import", "export", "switch", 
            "case", "default", "continue", "break", "return", "type", "enum", "function", "method", 
            "strategy", "indicator", "library", "true", "false", "na", "series", "simple", "const", 
            "input"
    };

    private static final String[] BUILT_IN_VARIABLES = {
            "open", "high", "low", "close", "volume", "time", "bar_index", "barstate.isconfirmed", 
            "barstate.isfirst", "barstate.islast", "barstate.ishistory", "barstate.isrealtime", 
            "syminfo.ticker", "syminfo.prefix", "syminfo.root", "syminfo.currency", 
            "syminfo.description", "syminfo.timezone", "syminfo.session", "syminfo.mintick"
    };

    private static final String[] NAMESPACES = {
            "ta", "math", "array", "matrix", "map", "str", "color", "chart", "strategy",
            "syminfo", "request", "ticker"
    };

    private static final String[] TYPES = {
            "int", "float", "bool", "string", "color", "label", "line", "box", "table"
    };

    // Argument and context awareness flags
    private boolean isInsideFunctionCall = false;
    private String currentFunctionName = null;
    private int currentParamIndex = 0;

    public PineScriptCompletionContributor() {
        LOG.info("PineScriptCompletionContributor initialized");
        
        // Add specific completion for namespace methods (after dot)
        extend(CompletionType.BASIC,
               PlatformPatterns.psiElement().withLanguage(PineScriptLanguage.INSTANCE),
               new CompletionProvider<>() {
                   @Override
                   protected void addCompletions(@NotNull CompletionParameters parameters,
                                                @NotNull ProcessingContext context,
                                                @NotNull CompletionResultSet result) {
                       Document document = parameters.getEditor().getDocument();
                       int offset = parameters.getOffset();
                       String documentText = document.getText();
                       
                       if (offset > 0) {
                           // Get text up to cursor position
                           String textBeforeCursor = documentText.substring(0, offset);
                           LOG.info("Text before cursor for namespace check: '" + textBeforeCursor + "'");
                           
                           // Special handling for namespace methods (after dot)
                           if (textBeforeCursor.endsWith(".")) {
                               String namespace = findNamespaceBeforeDot(textBeforeCursor);
                               LOG.info("Detected namespace before dot: " + namespace);
                               
                               if (namespace != null && Arrays.asList(NAMESPACES).contains(namespace)) {
                                   LOG.info("Adding methods for namespace: " + namespace);
                                   addNamespaceMethodCompletions(result, namespace);
                               }
                               return; // Stop processing after handling namespace methods
                           }
                           
                           // Special handling for function parameters
                           if (isInFunctionCall(textBeforeCursor)) {
                               String functionName = extractFunctionName(textBeforeCursor);
                               LOG.info("Detected inside function call: " + functionName);
                               
                               if (functionName != null) {
                                   addFunctionParameterCompletions(result, functionName);
                                   return; // Stop processing after handling function parameters
                               }
                           }
                       }
                       
                       // Continue with standard completions
                       processStandardCompletions(parameters, result);
                   }
               });
    }
    
    /**
     * Finds the namespace before a dot in the text.
     */
    private String findNamespaceBeforeDot(String text) {
        if (!text.endsWith(".")) {
            return null;
        }
        
        // Find the last word before the dot
        int lastNonWordChar = -1;
        for (int i = text.length() - 2; i >= 0; i--) {
            char c = text.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                lastNonWordChar = i;
                break;
            }
        }
        
        String word = text.substring(lastNonWordChar + 1, text.length() - 1);
        LOG.info("Found word before dot: " + word);
        
        return word;
    }
    
    /**
     * Determines if the cursor is within a function call.
     */
    private boolean isInFunctionCall(String text) {
        // Simple check: look for an open parenthesis that's not closed
        int openCount = 0;
        int closeCount = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') openCount++;
            else if (c == ')') closeCount++;
        }
        
        return openCount > closeCount;
    }
    
    /**
     * Extracts the function name from text before cursor.
     */
    private String extractFunctionName(String text) {
        // Find the last open parenthesis
        int lastOpenParen = text.lastIndexOf('(');
        if (lastOpenParen <= 0) {
            return null;
        }
        
        // Look backwards to find the function name
        int nameStart = lastOpenParen - 1;
        while (nameStart >= 0 && 
               (Character.isJavaIdentifierPart(text.charAt(nameStart)) || 
                text.charAt(nameStart) == '.')) {
            nameStart--;
        }
        nameStart++;
        
        if (nameStart < lastOpenParen) {
            return text.substring(nameStart, lastOpenParen);
        }
        
        return null;
    }
    
    /**
     * Process standard completions that are not specific to namespaces or function parameters.
     */
    private void processStandardCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        Document document = parameters.getEditor().getDocument();
        String documentText = document.getText();
        int offset = parameters.getOffset();
        
        // Check if we're inside a function call for parameter completion
        checkFunctionCallContext(documentText, offset);
        
        if (isInsideFunctionCall && currentFunctionName != null) {
            // Add parameter-specific completions
            LOG.info("Inside function call: " + currentFunctionName + ", param index: " + currentParamIndex);
            addParameterCompletions(result, currentFunctionName, currentParamIndex);
        }
        
        // Add standard completions
        LOG.info("Adding standard completions");
        addStandardCompletions(parameters, result, documentText, offset);
        
        // Add variables and functions from the current document
        addScannedCompletions(parameters, result);
    }
    
    /**
     * Adds method completions for a specific namespace.
     *
     * @param result The completion result set
     * @param namespace The namespace to add methods for
     */
    private void addNamespaceMethodCompletions(CompletionResultSet result, String namespace) {
        LOG.info("Adding method completions for namespace: " + namespace);
        
        String[] methods = NAMESPACE_METHODS.getOrDefault(namespace, new String[0]);
        LOG.info("Found " + methods.length + " methods for namespace: " + namespace);
        
        for (String method : methods) {
            // Check if we have detailed function info
            String fullMethodName = namespace + "." + method;
            PineScriptFunctionData.FunctionInfo[] functionInfos = 
                PineScriptFunctionData.getFunctionInfo(fullMethodName);
            
            // Build the lookup element
            LookupElementBuilder element;
            if (functionInfos != null && functionInfos.length > 0) {
                String paramList = String.join(", ", functionInfos[0].getParameters());
                String presentableText = method + "(" + paramList + ")";
                
                element = LookupElementBuilder.create(method)
                    .withPresentableText(presentableText)
                    .withTypeText(functionInfos[0].getReturnType())
                    .withTailText(" " + functionInfos[0].getDescription(), true)
                    .withIcon(AllIcons.Nodes.Method)
                    .withInsertHandler(new SmartInsertHandler(true, true));
            } else {
                element = LookupElementBuilder.create(method)
                    .withPresentableText(method)
                    .withTypeText(namespace + " method")
                    .withIcon(AllIcons.Nodes.Method)
                    .withInsertHandler(new SmartInsertHandler(true, true));
            }
            
            // Add with high priority to ensure namespace methods appear at the top
            result.addElement(PrioritizedLookupElement.withPriority(element, 2000));
        }
    }
    
    /**
     * Adds function parameter name completions for a function.
     */
    private void addFunctionParameterCompletions(CompletionResultSet result, String functionName) {
        LOG.info("Adding parameter completions for function: " + functionName);
        
        // Get text before cursor to determine prefix for filtering
        CompletionResultSet filteredResult = result;
        
        // Get parameters for this function
        Map<String, String> params = FUNCTION_PARAMETERS.getOrDefault(functionName, Collections.emptyMap());
        
        // If no specific parameters found and this is a namespace method, try with the namespace
        if (params.isEmpty() && functionName.contains(".")) {
            String namespace = functionName.split("\\.")[0];
            String method = functionName.split("\\.")[1];
            
            // Try to get function parameters from PineScriptFunctionData
            PineScriptFunctionData.FunctionInfo[] functionInfos = 
                PineScriptFunctionData.getFunctionInfo(functionName);
                
            if (functionInfos != null && functionInfos.length > 0) {
                for (String paramName : functionInfos[0].getParameters()) {
                    params.put(paramName + "=", "parameter");
                }
            }
        }
        
        // Add each parameter as a completion option
        for (Map.Entry<String, String> param : params.entrySet()) {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create(param.getKey())
                        .withTypeText(param.getValue())
                        .withIcon(AllIcons.Nodes.Parameter)
                        .withInsertHandler((context, item) -> {
                            // Position cursor after the equals sign
                            Editor editor = context.getEditor();
                            int offset = context.getTailOffset();
                            editor.getCaretModel().moveToOffset(offset);
                        }),
                    1000 // High priority
                )
            );
        }
    }
    
    /**
     * Detects if the current cursor position is inside a function call and extracts
     * the function name and parameter index.
     * 
     * @param documentText The text of the current document
     * @param offset The current cursor position
     */
    private void checkFunctionCallContext(String documentText, int offset) {
        isInsideFunctionCall = false;
        currentFunctionName = null;
        currentParamIndex = 0;
        
        // Find the most recent opening parenthesis
        int openParenIndex = -1;
        int parenCount = 0;
        
        for (int i = offset - 1; i >= 0 && i < documentText.length(); i--) {
            char c = documentText.charAt(i);
            if (c == ')') {
                parenCount++;
            } else if (c == '(') {
                parenCount--;
                if (parenCount < 0) {
                    openParenIndex = i;
                    break;
                }
            }
        }
        
        if (openParenIndex > 0) {
            // Look for function name before the parenthesis
            int nameStart = openParenIndex - 1;
            while (nameStart >= 0 && 
                   (Character.isJavaIdentifierPart(documentText.charAt(nameStart)) || 
                    documentText.charAt(nameStart) == '.')) {
                nameStart--;
            }
            nameStart++;
            
            if (nameStart < openParenIndex) {
                currentFunctionName = documentText.substring(nameStart, openParenIndex);
                isInsideFunctionCall = true;
                
                // Calculate which parameter we're on
                int commaCount = 0;
                parenCount = 0;
                for (int i = openParenIndex + 1; i < offset && i < documentText.length(); i++) {
                    char c = documentText.charAt(i);
                    if (c == '(') parenCount++;
                    else if (c == ')') parenCount--;
                    else if (c == ',' && parenCount == 0) commaCount++;
                }
                currentParamIndex = commaCount;
            }
        }
    }
    
    /**
     * Adds parameter-specific completions based on the function and parameter index.
     * 
     * @param result The completion result set
     * @param functionName The name of the function
     * @param paramIndex The index of the parameter
     */
    private void addParameterCompletions(CompletionResultSet result, String functionName, int paramIndex) {
        // Get appropriate suggestions based on the function and parameter index
        Map<String, String> suggestions = getParameterSuggestions(functionName, paramIndex);
        
        // Add the parameter names as well
        if (FUNCTION_PARAMETERS.containsKey(functionName)) {
            Map<String, String> params = FUNCTION_PARAMETERS.get(functionName);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                suggestions.put(entry.getKey() + "=", "named parameter");
            }
        }
        
        // Try to get function parameters from PineScriptFunctionData if it's a namespace method
        if (functionName.contains(".")) {
            PineScriptFunctionData.FunctionInfo[] functionInfos = 
                PineScriptFunctionData.getFunctionInfo(functionName);
                
            if (functionInfos != null && functionInfos.length > 0 && 
                paramIndex < functionInfos[0].getParameters().length) {
                String paramName = functionInfos[0].getParameters()[paramIndex];
                suggestions.put(paramName + "=", "named parameter");
            }
        }
        
        for (Map.Entry<String, String> entry : suggestions.entrySet()) {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create(entry.getKey())
                        .withTypeText("parameter")
                        .withTailText(" " + entry.getValue(), true)
                        .withIcon(AllIcons.Nodes.Parameter),
                    900 // High priority but lower than named parameters
                )
            );
        }
    }
    
    /**
     * Returns parameter-specific suggestions based on the function and parameter index.
     * 
     * @param functionName The name of the function
     * @param paramIndex The index of the parameter
     * @return A map of suggestions (value -> description)
     */
    private Map<String, String> getParameterSuggestions(String functionName, int paramIndex) {
        Map<String, String> suggestions = new HashMap<>();
        
        // Determine if this is a namespace method
        String namespace = null;
        String methodName = functionName;
        
        if (functionName.contains(".")) {
            String[] parts = functionName.split("\\.", 2);
            namespace = parts[0];
            methodName = parts[1];
        }
        
        // Special handling for specific functions
        if ("strategy.entry".equals(functionName)) {
            if (paramIndex == 1) { // direction parameter
                suggestions.put("\"long\"", "buy");
                suggestions.put("\"short\"", "sell");
            }
        } else if ("strategy.exit".equals(functionName)) {
            if (paramIndex == 1) { // from_entry parameter
                suggestions.put("\"id\"", "entry ID to exit from");
                suggestions.put("\"all\"", "exit all entries");
            } else if (paramIndex == 3) { // qty_percent parameter
                suggestions.put("100", "exit all quantity");
                suggestions.put("50", "exit half quantity");
            } else if (paramIndex == 4 || paramIndex == 6) { // profit or loss parameter
                suggestions.put("10", "price points");
            } else if (paramIndex >= 13 && paramIndex <= 16) { // comment parameters
                suggestions.put("\"Exit Signal\"", "exit comment");
                suggestions.put("\"Take Profit\"", "profit comment");
                suggestions.put("\"Stop Loss\"", "loss comment");
                suggestions.put("\"Trailing Stop\"", "trailing comment");
            }
        }
        
        // Get function info
        PineScriptFunctionData.FunctionInfo[] functionInfos = 
            PineScriptFunctionData.getFunctionInfo(functionName);
        
        if (functionInfos != null && functionInfos.length > 0 && 
            paramIndex < functionInfos[0].getParameters().length) {
            
            String paramName = functionInfos[0].getParameters()[paramIndex];
            
            // Add suggestions based on parameter type/name
            if (paramName.contains("color")) {
                for (String colorValue : NAMESPACE_METHODS.getOrDefault("color", new String[0])) {
                    suggestions.put(colorValue, "color value");
                }
                suggestions.put("color.rgb(255, 255, 255)", "white");
                suggestions.put("color.new(color.blue, 70)", "transparent blue");
            } else if (paramName.contains("series") || paramName.equals("source")) {
                for (String variable : BUILT_IN_VARIABLES) {
                    if (variable.contains("open") || variable.contains("high") || 
                        variable.contains("low") || variable.contains("close") || 
                        variable.contains("volume")) {
                        suggestions.put(variable, "price data");
                    }
                }
                suggestions.put("close", "closing price");
                suggestions.put("open", "opening price");
                suggestions.put("high", "highest price");
                suggestions.put("low", "lowest price");
            } else if (paramName.contains("length") || paramName.contains("size") || 
                      paramName.contains("periods")) {
                suggestions.put("14", "common period");
                suggestions.put("20", "common period");
                suggestions.put("50", "common period");
                suggestions.put("200", "common period");
            } else if (paramName.contains("title") || paramName.contains("text") ||
                      paramName.contains("comment") || paramName.contains("id") ||
                      paramName.contains("message")) {
                suggestions.put("\"My Indicator\"", "text");
                suggestions.put("\"Signal\"", "text");
                suggestions.put("\"Entry Signal\"", "text");
                suggestions.put("\"Exit Signal\"", "text");
            } else if (paramName.contains("style")) {
                suggestions.put("line.style_solid", "line style");
                suggestions.put("line.style_dotted", "line style");
                suggestions.put("line.style_dashed", "line style");
            } else if (paramName.contains("direction")) {
                suggestions.put("\"long\"", "buy");
                suggestions.put("\"short\"", "sell");
            } else if (paramName.contains("disable") || paramName.contains("alert")) {
                suggestions.put("true", "boolean");
                suggestions.put("false", "boolean");
            }
            
            // Add generic true/false for boolean parameters
            if (paramName.contains("bool") || paramName.endsWith("ed") || 
                paramName.contains("disable") || paramName.contains("enable")) {
                suggestions.put("true", "boolean");
                suggestions.put("false", "boolean");
            }
        }
        
        return suggestions;
    }
    
    /**
     * Adds standard Pine Script completions like keywords, built-in variables, and functions.
     * 
     * @param parameters The completion parameters
     * @param result The completion result set
     * @param documentText The text of the current document
     * @param offset The current cursor position
     */
    private void addStandardCompletions(@NotNull CompletionParameters parameters, 
                                       @NotNull CompletionResultSet result,
                                       String documentText, int offset) {
        LOG.info("Adding keywords, built-ins, and other completions");
        
        // Add keyword completions
        for (String keyword : KEYWORDS) {
            LookupElementBuilder element = LookupElementBuilder.create(keyword)
                .bold()
                .withTypeText("keyword")
                .withIcon(AllIcons.Nodes.Favorite);
            
            result.addElement(element);
        }
        
        // Add built-in variables
        for (String variable : BUILT_IN_VARIABLES) {
            result.addElement(LookupElementBuilder.create(variable)
                .withTypeText("built-in")
                .withIcon(AllIcons.Nodes.Variable));
        }
        
        // Add namespaces - with higher priority
        for (String namespace : NAMESPACES) {
            // Give namespaces a higher priority so they appear at the top of suggestions
            result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(namespace)
                    .withTypeText("namespace")
                    .withIcon(AllIcons.Nodes.Package)
                    .withTailText(".", true)
                    .withInsertHandler((context, item) -> {
                        // Add a dot after the namespace when it's selected, to trigger method suggestions
                        try {
                            Editor editor = context.getEditor();
                            Document document = editor.getDocument();
                            int tailOffset = context.getTailOffset();
                            
                            // Check if there's already a dot
                            boolean hasDot = tailOffset < document.getTextLength() && 
                                           document.getText().charAt(tailOffset) == '.';
                            
                            // Add a dot if there isn't one already
                            if (!hasDot) {
                                document.insertString(tailOffset, ".");
                                editor.getCaretModel().moveToOffset(tailOffset + 1);
                                
                                // Force re-trigger completion with application thread
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    AutoPopupController.getInstance(editor.getProject())
                                        .scheduleAutoPopup(editor, CompletionType.BASIC, null);
                                });
                            }
                        } catch (Exception e) {
                            LOG.error("Error in namespace insert handler", e);
                        }
                    }),
                1000 // High priority for namespaces
            ));
        }
        
        // Add types
        for (String type : TYPES) {
            result.addElement(LookupElementBuilder.create(type)
                .withTypeText("type")
                .withIcon(AllIcons.Nodes.Class));
        }
        
        // Add function completions
        for (Map.Entry<String, PineScriptFunctionData.FunctionInfo[]> entry : 
                PineScriptFunctionData.getFunctionMap().entrySet()) {
            for (PineScriptFunctionData.FunctionInfo functionInfo : entry.getValue()) {
                String presentableText = functionInfo.getName() + "(" + 
                    String.join(", ", functionInfo.getParameters()) + ")";
                
                LookupElementBuilder element = LookupElementBuilder.create(functionInfo.getName())
                    .withPresentableText(presentableText)
                    .withTypeText(functionInfo.getReturnType())
                    .withTailText(" " + functionInfo.getDescription(), true)
                    .withIcon(AllIcons.Nodes.Function)
                    .withInsertHandler(new SmartInsertHandler(true, true));
                
                result.addElement(element);
            }
        }
    }
    
    /**
     * Adds completions for variables, functions, and types found in the current document.
     * 
     * @param parameters The completion parameters
     * @param result The completion result set
     */
    private void addScannedCompletions(@NotNull CompletionParameters parameters, 
                                      @NotNull CompletionResultSet result) {
        LOG.info("Adding scanned completions from document");
        
        try {
            // Scan the document for declarations
            Map<String, Object> scanResults = PineScriptScanner.scanDocument(parameters);
            
            // Add variables
            @SuppressWarnings("unchecked")
            List<PineScriptScanner.VarInfo> vars = 
                (List<PineScriptScanner.VarInfo>) scanResults.get("variables");
            if (vars != null) {
                for (PineScriptScanner.VarInfo var : vars) {
                    result.addElement(LookupElementBuilder.create(var.name)
                        .withTypeText(var.type.isEmpty() ? "var" : var.type)
                        .withIcon(AllIcons.Nodes.Variable));
                }
            }
            
            // Add functions
            @SuppressWarnings("unchecked")
            List<PineScriptScanner.FunctionInfo> functions = 
                (List<PineScriptScanner.FunctionInfo>) scanResults.get("functions");
            if (functions != null) {
                for (PineScriptScanner.FunctionInfo function : functions) {
                    String params = function.params.stream()
                        .map(p -> p.name)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                    
                    String presentableText = function.name + "(" + params + ")";
                    
                    result.addElement(LookupElementBuilder.create(function.name)
                        .withPresentableText(presentableText)
                        .withTypeText("function")
                        .withIcon(AllIcons.Nodes.Function)
                        .withInsertHandler(new SmartInsertHandler(true, true)));
                }
            }
            
            // Add methods
            @SuppressWarnings("unchecked")
            List<PineScriptScanner.FunctionInfo> methods = 
                (List<PineScriptScanner.FunctionInfo>) scanResults.get("methods");
            if (methods != null) {
                for (PineScriptScanner.FunctionInfo method : methods) {
                    String params = method.params.stream()
                        .map(p -> p.name)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                    
                    String presentableText = method.name + "(" + params + ")";
                    
                    result.addElement(LookupElementBuilder.create(method.name)
                        .withPresentableText(presentableText)
                        .withTypeText("method")
                        .withIcon(AllIcons.Nodes.Method)
                        .withInsertHandler(new SmartInsertHandler(true, true)));
                }
            }
            
            // Add types
            @SuppressWarnings("unchecked")
            Map<String, Integer> types = (Map<String, Integer>) scanResults.get("types");
            if (types != null) {
                for (String type : types.keySet()) {
                    result.addElement(LookupElementBuilder.create(type)
                        .withTypeText("type")
                        .withIcon(AllIcons.Nodes.Class));
                }
            }
        } catch (Exception e) {
            LOG.error("Error adding scanned completions", e);
        }
    }
    
    /**
     * Initialize the map of namespace methods.
     * 
     * @return The initialized map
     */
    private static Map<String, String[]> initNamespaceMethods() {
        Map<String, String[]> map = new HashMap<>();
        
        // TA namespace methods
        map.put("ta", new String[]{
            "sma", "ema", "rma", "wma", "vwma", "swma", "dema", "tema", "alma", "rsi", "tr", "atr", 
            "sar", "supertrend", "macd", "bb", "stoch", "obv", "vwap", "cci", "mom", "mfi", "cmo", 
            "kc", "pivot", "highest", "lowest", "valuewhen", "cross", "crossover", "crossunder"
        });
        
        // Math namespace methods
        map.put("math", new String[]{
            "abs", "avg", "ceil", "exp", "floor", "log", "log10", "max", "min", "pow", "round", 
            "sign", "sqrt", "sum", "tan", "cos", "sin", "asin", "acos", "atan", "random"
        });
        
        // String namespace methods
        map.put("str", new String[]{
            "format", "tostring", "length", "lower", "upper", "replace", "split", "startswith", 
            "endswith", "substring", "tonumber", "contains", "pos", "trim"
        });
        
        // Color namespace methods
        map.put("color", new String[]{
            "rgb", "new", "red", "green", "blue", "white", "black", "yellow", "orange", "purple", 
            "lime", "teal", "navy", "maroon", "olive", "aqua", "fuchsia", "silver", "gray"
        });
        
        // Array namespace methods
        map.put("array", new String[]{
            "new_float", "new_int", "new_bool", "new_string", "new_color", "size", "get", "set", 
            "push", "pop", "insert", "remove", "shift", "unshift", "slice", "sort", "avg", "min", 
            "max", "median", "variance", "stdev", "covariance", "correlation", "join", "concat"
        });
        
        // Chart namespace methods
        map.put("chart", new String[]{
            "point", "bar_index", "is_visible", "is_last_visible_bar", "is_hovered", "is_realtime",
            "set_bgcolor", "set_timezone", "get_yaxis", "set_frame_width", "set_crosshair_pos"
        });
        
        // Strategy namespace methods
        map.put("strategy", new String[]{
            "entry", "exit", "close", "close_all", "cancel", "cancel_all", "risk.allow_entry_in", 
            "risk.max_cons_loss_days", "risk.max_drawdown", "risk.max_intraday_loss", "initial_capital", 
            "currency", "commission.cash_per_contract", "commission.percent", "slippage", "margin_long", 
            "margin_short", "opentrades", "position_size", "position_avg_price", "equity", "netprofit"
        });
        
        // Map namespace methods
        map.put("map", new String[]{
            "new", "get", "set", "remove", "contains", "keys", "values", "size", "clear"
        });
        
        // Matrix namespace methods
        map.put("matrix", new String[]{
            "new", "get", "set", "rows", "columns", "reshape", "submatrix", "inv", "transpose",
            "det", "eigenvalues", "mean", "avg", "sum", "add", "mult", "pow"
        });
        
        // Request namespace methods
        map.put("request", new String[]{
            "security", "financial", "quandl", "security_lower_tf", "dividends", "earnings", "splits"
        });
        
        // Syminfo namespace methods
        map.put("syminfo", new String[]{
            "ticker", "prefix", "root", "currency", "description", "timezone", "session", 
            "session_regular", "session_extended", "mintick", "pointvalue", "type", "industry", "sector"
        });
        
        return map;
    }
    
    /**
     * Initialize the map of function parameters.
     * 
     * @return The initialized map
     */
    private static Map<String, Map<String, String>> initFunctionParameters() {
        Map<String, Map<String, String>> functionParams = new HashMap<>();
        
        // Strategy.entry parameters - syntax: strategy.entry(id, direction, qty, limit, stop, oca_name, oca_type, comment, alert_message, disable_alert)
        Map<String, String> strategyEntryParams = new HashMap<>();
        strategyEntryParams.put("id", "string");
        strategyEntryParams.put("direction", "string");
        strategyEntryParams.put("qty", "float");
        strategyEntryParams.put("limit", "float");
        strategyEntryParams.put("stop", "float");
        strategyEntryParams.put("oca_name", "string");
        strategyEntryParams.put("oca_type", "string");
        strategyEntryParams.put("comment", "string");
        strategyEntryParams.put("alert_message", "string");
        strategyEntryParams.put("disable_alert", "boolean");
        functionParams.put("strategy.entry", strategyEntryParams);
        
        // Strategy.exit parameters - syntax: strategy.exit(id, from_entry, qty, qty_percent, profit, limit, loss, stop, trail_price, trail_points, trail_offset, oca_name, comment, comment_profit, comment_loss, comment_trailing, alert_message, alert_profit, alert_loss, alert_trailing, disable_alert)
        Map<String, String> strategyExitParams = new HashMap<>();
        strategyExitParams.put("id", "string");
        strategyExitParams.put("from_entry", "string");
        strategyExitParams.put("qty", "float");
        strategyExitParams.put("qty_percent", "float");
        strategyExitParams.put("profit", "float");
        strategyExitParams.put("limit", "float");
        strategyExitParams.put("loss", "float");
        strategyExitParams.put("stop", "float");
        strategyExitParams.put("trail_price", "float");
        strategyExitParams.put("trail_points", "float");
        strategyExitParams.put("trail_offset", "float");
        strategyExitParams.put("oca_name", "string");
        strategyExitParams.put("comment", "string");
        strategyExitParams.put("comment_profit", "string");
        strategyExitParams.put("comment_loss", "string");
        strategyExitParams.put("comment_trailing", "string");
        strategyExitParams.put("alert_message", "string");
        strategyExitParams.put("alert_profit", "string");
        strategyExitParams.put("alert_loss", "string");
        strategyExitParams.put("alert_trailing", "string");
        strategyExitParams.put("disable_alert", "boolean");
        functionParams.put("strategy.exit", strategyExitParams);
        
        // Indicator parameters
        Map<String, String> indicatorParams = new HashMap<>();
        indicatorParams.put("title", "string");
        indicatorParams.put("shorttitle", "string");
        indicatorParams.put("overlay", "boolean");
        indicatorParams.put("format", "string");
        indicatorParams.put("precision", "integer");
        indicatorParams.put("scale", "string");
        indicatorParams.put("max_bars_back", "integer");
        indicatorParams.put("timeframe", "string");
        functionParams.put("indicator", indicatorParams);
        
        // Plot parameters
        Map<String, String> plotParams = new HashMap<>();
        plotParams.put("title", "string");
        plotParams.put("color", "color");
        plotParams.put("linewidth", "integer");
        plotParams.put("style", "integer");
        plotParams.put("trackprice", "boolean");
        plotParams.put("histbase", "float");
        plotParams.put("offset", "integer");
        plotParams.put("join", "boolean");
        plotParams.put("editable", "boolean");
        plotParams.put("show_last", "integer");
        functionParams.put("plot", plotParams);
        
        // Input parameters
        Map<String, String> inputParams = new HashMap<>();
        inputParams.put("defval", "value");
        inputParams.put("title", "string");
        inputParams.put("tooltip", "string");
        inputParams.put("inline", "string");
        inputParams.put("group", "string");
        inputParams.put("confirm", "boolean");
        functionParams.put("input", inputParams);
        
        return functionParams;
    }
} 