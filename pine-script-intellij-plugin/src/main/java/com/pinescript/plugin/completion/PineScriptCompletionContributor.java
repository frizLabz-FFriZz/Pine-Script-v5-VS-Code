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
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.pinescript.plugin.completion.handlers.SmartInsertHandler;
import com.pinescript.plugin.language.PineScriptLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class PineScriptCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(PineScriptCompletionContributor.class);
    
    // Cache for storing loaded definition maps
    private static final Map<String, List<String>> CACHED_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String[]>> NAMESPACE_METHODS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Map<String, String>>> FUNCTION_PARAMETERS_CACHE = new ConcurrentHashMap<>();
    // Track which definitions are functions, variables or constants
    private static final Map<String, Set<String>> FUNCTIONS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> VARIABLES_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CONSTANTS_MAP = new ConcurrentHashMap<>();
    // Store function arguments from JSON definition files
    private static final Map<String, Map<String, List<Map<String, String>>>> FUNCTION_ARGUMENTS_CACHE = new ConcurrentHashMap<>();

    // Default to version 5 if version is not specified
    private static final String DEFAULT_VERSION = "5";
    
    // Pattern to match version in Pine Script files
    private static final Pattern VERSION_PATTERN = Pattern.compile("//@version=(\\d+)");

    // Static initialization to load definitions for default version on startup
    static {
        loadDefinitionsForVersion(DEFAULT_VERSION);
    }

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
                       
                       // Detect Pine Script version from the document
                       String version = detectPineScriptVersion(documentText);
                       // Ensure definitions for this version are loaded
                       if (!CACHED_DEFINITIONS.containsKey(version)) {
                           loadDefinitionsForVersion(version);
                       }
                       
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
                                   addNamespaceMethodCompletions(result, namespace, version);
                               }
                               return; // Stop processing after handling namespace methods
                           }
                           
                           // Special handling for function parameters - detect if inside parentheses
                           if (isInFunctionCall(textBeforeCursor)) {
                               String functionName = extractFunctionName(textBeforeCursor);
                               LOG.info("Detected inside function call: " + functionName);
                               
                               if (functionName != null) {
                                   // Check function call context to determine current parameter
                                   checkFunctionCallContext(documentText, offset);
                                   
                                   if (isInsideFunctionCall && currentFunctionName != null) {
                                       LOG.info("Suggesting parameters for: " + currentFunctionName + ", param index: " + currentParamIndex);
                                       addParameterCompletions(result, currentFunctionName, currentParamIndex, version);
                                   } else {
                                       // Fallback to function parameters if context check failed
                                       addFunctionParameterCompletions(result, functionName, version);
                                   }
                                   return; // Stop processing after handling function parameters
                               }
                           }
                       }
                       
                       // Continue with standard completions
                       processStandardCompletions(parameters, result, version);
                   }
               });
                
        // Register completion auto-popup trigger for parentheses and commas
        ApplicationManager.getApplication().invokeLater(() -> {
            CompletionAutoPopupHandler.install(null);
        });
    }
    
    /**
     * Detects the Pine Script version from the document text.
     * @param documentText The full text of the document
     * @return The detected version as a string, or the default version if not found
     */
    private String detectPineScriptVersion(String documentText) {
        Matcher matcher = VERSION_PATTERN.matcher(documentText);
        if (matcher.find()) {
            String version = matcher.group(1);
            LOG.info("Detected Pine Script version: " + version);
            return version;
        }
        LOG.info("No Pine Script version found, using default: " + DEFAULT_VERSION);
        return DEFAULT_VERSION;
    }
    
    /**
     * Loads all JSON definition files for a specific Pine Script version.
     * @param version The Pine Script version
     */
    private static synchronized void loadDefinitionsForVersion(String version) {
        if (CACHED_DEFINITIONS.containsKey(version)) {
            LOG.info("Definitions for version " + version + " already loaded");
            return;
        }
        
        LOG.info("Loading definitions for Pine Script version: " + version);
        
        // Load variables
        List<String> variableNames = loadNamesFromDefinitionFile(version, "variables.json");
        VARIABLES_MAP.put(version, new HashSet<>(variableNames));
        
        // Load constants
        List<String> constantNames = loadNamesFromDefinitionFile(version, "constants.json");
        CONSTANTS_MAP.put(version, new HashSet<>(constantNames));
        
        // Load functions
        List<String> functionNames = loadNamesFromDefinitionFile(version, "functions.json");
        Set<String> functionsSet = new HashSet<>();
        // Store clean function names (without parentheses) for proper completion
        List<String> cleanFunctionNames = new ArrayList<>();
        for (String funcName : functionNames) {
            String cleanName = funcName;
            if (cleanName.endsWith("()")) {
                cleanName = cleanName.substring(0, cleanName.length() - 2);
            }
            functionsSet.add(cleanName);
            cleanFunctionNames.add(cleanName);
        }
        FUNCTIONS_MAP.put(version, functionsSet);

        // Load function arguments from functions.json
        Map<String, List<Map<String, String>>> functionArgs = loadFunctionArguments(version);
        FUNCTION_ARGUMENTS_CACHE.put(version, functionArgs);
        
        // Combine all definitions
        List<String> allDefinitions = new ArrayList<>();
        allDefinitions.addAll(variableNames);
        allDefinitions.addAll(constantNames);
        allDefinitions.addAll(cleanFunctionNames);
        
        // Cache the combined definitions
        CACHED_DEFINITIONS.put(version, allDefinitions);
        
        // Also initialize namespace methods for this version
        NAMESPACE_METHODS_CACHE.put(version, initNamespaceMethodsForVersion(version, cleanFunctionNames));
        
        // And function parameters
        FUNCTION_PARAMETERS_CACHE.put(version, initFunctionParametersForVersion(version));
        
        LOG.info("Loaded " + allDefinitions.size() + " definitions for version " + version);
    }
    
    /**
     * Loads names from a specific JSON definition file.
     * @param version The Pine Script version
     * @param filename The JSON file name
     * @return A list of names from the JSON file
     */
    private static List<String> loadNamesFromDefinitionFile(String version, String filename) {
        List<String> names = new ArrayList<>();
        String resourcePath = "/definitions/v" + version + "/" + filename;
        
        try (InputStream is = PineScriptCompletionContributor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Resource not found: " + resourcePath);
                return names;
            }
            
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                if (item.has("name")) {
                    String name = item.getString("name");
                    // For functions.json, store the original name for display but strip "()" for lookup
                    if (filename.equals("functions.json") && name.endsWith("()")) {
                        names.add(name.substring(0, name.length() - 2));
                    } else {
                        names.add(name);
                    }
                }
            }
            
            LOG.info("Loaded " + names.size() + " names from " + resourcePath);
        } catch (IOException e) {
            LOG.error("Error loading definitions from " + resourcePath, e);
        }
        
        return names;
    }
    
    /**
     * Loads function arguments from the functions.json file
     * @param version The Pine Script version
     * @return A map of function names to their argument lists
     */
    private static Map<String, List<Map<String, String>>> loadFunctionArguments(String version) {
        Map<String, List<Map<String, String>>> result = new HashMap<>();
        String resourcePath = "/definitions/v" + version + "/functions.json";
        
        try (InputStream is = PineScriptCompletionContributor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Resource not found: " + resourcePath);
                return result;
            }
            
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                if (item.has("name") && item.has("arguments")) {
                    String name = item.getString("name");
                    // Remove trailing parentheses if they exist
                    if (name.endsWith("()")) {
                        name = name.substring(0, name.length() - 2);
                    }
                    
                    List<Map<String, String>> argList = new ArrayList<>();
                    JSONArray arguments = item.getJSONArray("arguments");
                    
                    for (int j = 0; j < arguments.length(); j++) {
                        JSONObject arg = arguments.getJSONObject(j);
                        Map<String, String> argMap = new HashMap<>();
                        
                        if (arg.has("argument")) {
                            argMap.put("name", arg.getString("argument"));
                        }
                        
                        if (arg.has("type")) {
                            argMap.put("type", arg.getString("type"));
                        }
                        
                        argList.add(argMap);
                    }
                    
                    result.put(name, argList);
                }
            }
            
            LOG.info("Loaded arguments for " + result.size() + " functions from " + resourcePath);
        } catch (IOException e) {
            LOG.error("Error loading function arguments from " + resourcePath, e);
        }
        
        return result;
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
                                           @NotNull CompletionResultSet result,
                                           String version) {
        PsiElement position = parameters.getPosition();
        Document document = parameters.getEditor().getDocument();
        String documentText = document.getText();
        int offset = parameters.getOffset();
        
        // Check if we're inside a function call for parameter completion
        checkFunctionCallContext(documentText, offset);
        
        if (isInsideFunctionCall && currentFunctionName != null) {
            // Add parameter-specific completions
            LOG.info("Inside function call: " + currentFunctionName + ", param index: " + currentParamIndex);
            addParameterCompletions(result, currentFunctionName, currentParamIndex, version);
        }
        
        // Add standard completions
        LOG.info("Adding standard completions for version: " + version);
        addStandardCompletions(parameters, result, documentText, offset, version);
        
        // Add variables and functions from the current document
        addScannedCompletions(parameters, result);
    }
    
    /**
     * Adds namespace method completions to the result.
     */
    private void addNamespaceMethodCompletions(CompletionResultSet result, String namespace, String version) {
        Map<String, String[]> namespaceMethods = NAMESPACE_METHODS_CACHE.getOrDefault(version, 
                                                 initNamespaceMethodsForVersion(version, CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>())));
        
        if (namespaceMethods.containsKey(namespace)) {
            String[] methods = namespaceMethods.get(namespace);
            for (String method : methods) {
                LookupElementBuilder element = LookupElementBuilder.create(method)
                        .withIcon(AllIcons.Nodes.Method)
                        .withTypeText(namespace + " method")
                        .withInsertHandler((ctx, item) -> {
                            // Add parentheses for methods and position caret inside them
                            Editor editor = ctx.getEditor();
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Trigger parameter info popup
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).autoPopupParameterInfo(editor, null);
                            });
                        });
                result.addElement(element);
            }
        }
    }
    
    /**
     * Adds function parameter completions to the result.
     */
    private void addFunctionParameterCompletions(CompletionResultSet result, String functionName, String version) {
        Map<String, Map<String, String>> functionParams = FUNCTION_PARAMETERS_CACHE.getOrDefault(version, 
                                                          initFunctionParametersForVersion(version));
        
        if (functionParams.containsKey(functionName)) {
            Map<String, String> params = functionParams.get(functionName);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String paramName = entry.getKey();
                String paramType = entry.getValue();
                
                LookupElementBuilder element = LookupElementBuilder.create(paramName + "=")
                        .withIcon(AllIcons.Nodes.Parameter)
                        .withTypeText(paramType)
                        .withInsertHandler((ctx, item) -> {
                            // Move caret after equals sign to let user input the value
                            Editor editor = ctx.getEditor();
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 100));
            }
        }
    }
    
    /**
     * Check if we're inside a function call and determine the current parameter index.
     */
    private void checkFunctionCallContext(String documentText, int offset) {
        isInsideFunctionCall = false;
        currentFunctionName = null;
        currentParamIndex = 0;
        
        // Check if we're inside a function call
        int openParenCount = 0;
        int commaCount = 0;
        boolean insideString = false;
        int functionNameStart = -1;
        
        for (int i = 0; i < offset; i++) {
            char c = documentText.charAt(i);
            
            if (c == '"' && (i == 0 || documentText.charAt(i - 1) != '\\')) {
                insideString = !insideString;
            }
            
            if (!insideString) {
                if (c == '(') {
                    if (openParenCount == 0) {
                        // Look backwards to find function name
                        functionNameStart = i - 1;
                        while (functionNameStart >= 0 && 
                               (Character.isJavaIdentifierPart(documentText.charAt(functionNameStart)) || 
                                documentText.charAt(functionNameStart) == '.')) {
                            functionNameStart--;
                        }
                        functionNameStart++;
                    }
                    openParenCount++;
                } else if (c == ')') {
                    openParenCount--;
                    if (openParenCount == 0) {
                        functionNameStart = -1;
                        commaCount = 0;
                    }
                } else if (c == ',' && openParenCount > 0) {
                    commaCount++;
                }
            }
        }
        
        if (openParenCount > 0 && functionNameStart >= 0 && functionNameStart < offset) {
            isInsideFunctionCall = true;
            currentParamIndex = commaCount;
            
            // Extract the function name
            int functionNameEnd = documentText.indexOf('(', functionNameStart);
            if (functionNameEnd > functionNameStart) {
                currentFunctionName = documentText.substring(functionNameStart, functionNameEnd);
                LOG.info("Inside function call: " + currentFunctionName + ", parameter index: " + currentParamIndex);
            }
        }
    }
    
    /**
     * Adds parameter-specific completions for the given function and parameter index.
     */
    private void addParameterCompletions(CompletionResultSet result, String functionName, int paramIndex, String version) {
        // Get function arguments from cache for this version
        Map<String, List<Map<String, String>>> functionArgs = FUNCTION_ARGUMENTS_CACHE.getOrDefault(version, new HashMap<>());
        
        // If we have argument definitions for this function
        if (functionArgs.containsKey(functionName)) {
            List<Map<String, String>> args = functionArgs.get(functionName);
            
            // If there are arguments defined and the current parameter index is valid
            if (!args.isEmpty() && paramIndex < args.size()) {
                // Get the current parameter information
                Map<String, String> param = args.get(paramIndex);
                String paramName = param.getOrDefault("name", "");
                String paramType = param.getOrDefault("type", "");
                
                if (!paramName.isEmpty()) {
                    // Add named parameter option
                    LookupElementBuilder namedElement = LookupElementBuilder.create(paramName + "=")
                            .withIcon(AllIcons.Nodes.Parameter)
                            .withTypeText(paramType)
                            .withTailText(" (named)", true)
                            .withInsertHandler((ctx, item) -> {
                                // Move caret after equals sign to let user input the value
                                Editor editor = ctx.getEditor();
                                editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                            });
                    result.addElement(PrioritizedLookupElement.withPriority(namedElement, 300));
                    
                    // Also suggest possible values based on parameter type
                    Map<String, String> valueSuggestions = getValueSuggestionsForType(paramType, paramName, functionName, paramIndex);
                    for (Map.Entry<String, String> entry : valueSuggestions.entrySet()) {
                        LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                                .withTypeText(entry.getValue())
                                .withIcon(AllIcons.Nodes.Parameter);
                        result.addElement(PrioritizedLookupElement.withPriority(element, 250));
                    }
                }
            }
            
            // Suggest all other parameter names (for named parameters)
            for (int i = 0; i < args.size(); i++) {
                if (i != paramIndex) { // Skip current parameter as it's already suggested above
                    Map<String, String> otherParam = args.get(i);
                    String otherParamName = otherParam.getOrDefault("name", "");
                    String otherParamType = otherParam.getOrDefault("type", "");
                    
                    if (!otherParamName.isEmpty()) {
                        LookupElementBuilder element = LookupElementBuilder.create(otherParamName + "=")
                                .withIcon(AllIcons.Nodes.Parameter)
                                .withTypeText(otherParamType)
                                .withTailText(" (named)", true)
                                .withInsertHandler((ctx, item) -> {
                                    // Move caret after equals sign to let user input the value
                                    Editor editor = ctx.getEditor();
                                    editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                                });
                        result.addElement(PrioritizedLookupElement.withPriority(element, 200));
                    }
                }
            }
        }
        
        // Check if we have special value completions for this parameter
        Map<String, String> specialSuggestions = getParameterSuggestions(functionName, paramIndex);
        if (!specialSuggestions.isEmpty()) {
            for (Map.Entry<String, String> entry : specialSuggestions.entrySet()) {
                LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                        .withTypeText(entry.getValue())
                        .withIcon(AllIcons.Nodes.Parameter);
                result.addElement(PrioritizedLookupElement.withPriority(element, 220));
            }
        }
    }
    
    /**
     * Returns value suggestions based on parameter type
     */
    private Map<String, String> getValueSuggestionsForType(String paramType, String paramName, String functionName, int paramIndex) {
        Map<String, String> suggestions = new HashMap<>();
        
        // Suggest values based on parameter type
        if (paramType.contains("bool") || paramType.contains("boolean")) {
            suggestions.put("true", "boolean value");
            suggestions.put("false", "boolean value");
        } else if (paramType.contains("string")) {
            suggestions.put("\"text\"", "string value");
            
            // If parameter name suggests a specific type of string
            if (paramName.contains("id") || paramName.equals("id")) {
                suggestions.put("\"myId\"", "identifier");
            } else if (paramName.contains("title") || paramName.contains("name")) {
                suggestions.put("\"My Title\"", "title");
            } else if (paramName.contains("comment")) {
                suggestions.put("\"Comment\"", "comment text");
            }
        } else if (paramType.contains("color")) {
            suggestions.put("color.blue", "blue");
            suggestions.put("color.red", "red");
            suggestions.put("color.green", "green");
            suggestions.put("color.yellow", "yellow");
            suggestions.put("color.purple", "purple");
            suggestions.put("color.orange", "orange");
            suggestions.put("color.white", "white");
            suggestions.put("color.black", "black");
        } else if (paramType.contains("int")) {
            suggestions.put("0", "integer");
            suggestions.put("1", "integer");
            suggestions.put("10", "integer");
            
            // Suggest appropriate values based on parameter name
            if (paramName.contains("length") || paramName.contains("period")) {
                suggestions.put("14", "period");
                suggestions.put("20", "period");
                suggestions.put("50", "period");
                suggestions.put("200", "period");
            } else if (paramName.contains("width") || paramName.contains("size")) {
                suggestions.put("1", "size");
                suggestions.put("2", "size");
                suggestions.put("3", "size");
            }
        } else if (paramType.contains("float")) {
            suggestions.put("0.0", "float");
            suggestions.put("1.0", "float");
            
            // If parameter relates to trading quantity
            if (paramName.contains("qty") || paramName.contains("quantity")) {
                suggestions.put("1.0", "quantity");
                suggestions.put("0.5", "quantity");
            }
        } else if (paramType.contains("array")) {
            // For array parameters
            if (paramName.equals("id")) {
                suggestions.put("myArray", "array variable");
            }
        } else if (paramType.contains("series")) {
            // Suggest common series values
            suggestions.put("close", "price series");
            suggestions.put("open", "price series");
            suggestions.put("high", "price series");
            suggestions.put("low", "price series");
            suggestions.put("volume", "volume series");
            suggestions.put("time", "time series");
        }
        
        return suggestions;
    }
    
    /**
     * Returns special suggestions for specific function parameters.
     * This provides predefined suggestions for common function parameters
     * that are not easily derived from the type information.
     */
    private Map<String, String> getParameterSuggestions(String functionName, int paramIndex) {
        Map<String, String> suggestions = new HashMap<>();
        
        // Special case for strategy.entry direction parameter (1)
        if (functionName.equals("strategy.entry") && paramIndex == 1) {
            suggestions.put("\"long\"", "buy");
            suggestions.put("\"short\"", "sell");
        }
        // Special case for strategy.exit from_entry parameter (1)
        else if (functionName.equals("strategy.exit") && paramIndex == 1) {
            suggestions.put("\"id\"", "entry ID to exit from");
            suggestions.put("\"all\"", "exit all entries");
        }
        // Special case for strategy.exit qty_percent parameter (3)
        else if (functionName.equals("strategy.exit") && paramIndex == 3) {
            suggestions.put("100", "exit all quantity");
            suggestions.put("50", "exit half quantity");
        }
        // Special case for strategy.exit profit/loss parameters (4, 6)
        else if (functionName.equals("strategy.exit") && (paramIndex == 4 || paramIndex == 6)) {
            suggestions.put("10", "price points");
        }
        // Special case for strategy.exit comment parameters (13-16)
        else if (functionName.equals("strategy.exit") && (paramIndex >= 13 && paramIndex <= 16)) {
            suggestions.put("\"Exit Signal\"", "exit comment");
            suggestions.put("\"Take Profit\"", "profit exit comment");
            suggestions.put("\"Stop Loss\"", "loss exit comment");
            suggestions.put("\"Trailing Stop\"", "trailing exit comment");
        }
        // Color parameters suggestions
        else if (functionName.endsWith("color") || 
                (functionName.contains("bgcolor") || functionName.contains("textcolor"))) {
            suggestions.put("color.blue", "blue color");
            suggestions.put("color.red", "red color");
            suggestions.put("color.green", "green color");
            suggestions.put("color.yellow", "yellow color");
            suggestions.put("color.purple", "purple color");
            suggestions.put("color.orange", "orange color");
        }
        
        return suggestions;
    }
    
    /**
     * Adds standard completions to the result.
     */
    private void addStandardCompletions(@NotNull CompletionParameters parameters, 
                                       @NotNull CompletionResultSet result,
                                       String documentText, int offset,
                                       String version) {
        // Add keywords with high priority
        for (String keyword : KEYWORDS) {
            LookupElementBuilder element = LookupElementBuilder.create(keyword)
                    .withIcon(AllIcons.Nodes.Favorite)
                    .withTypeText("keyword");
            result.addElement(PrioritizedLookupElement.withPriority(element, 1000));
        }
        
        // Get sets for this version
        Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
        Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
        Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
        
        // Add built-in variables from the cached definitions
        List<String> definitions = CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>());
        for (String definition : definitions) {
            LookupElementBuilder element;
            if (Arrays.asList(NAMESPACES).contains(definition)) {
                // For namespaces, add a dot automatically
                element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Package)
                        .withTypeText("namespace")
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Trigger autocompletion for namespace methods
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).autoPopupMemberLookup(editor, null);
                            });
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 900));
            } else if (functions.contains(definition)) {
                // Function with appropriate icon
                element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Function)
                        .withTypeText("function")
                        .withTailText("()", true)
                        .withInsertHandler((ctx, item) -> {
                            // Add parentheses for functions and position cursor inside
                            Editor editor = ctx.getEditor();
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Trigger parameter info popup
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).autoPopupParameterInfo(editor, null);
                            });
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 800));
            } else if (constants.contains(definition)) {
                // Constant with appropriate icon
                element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Constant)
                        .withTypeText("constant")
                        .withBoldness(true);
                result.addElement(PrioritizedLookupElement.withPriority(element, 850));
            } else {
                // Variable with appropriate icon
                element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTypeText("variable");
                result.addElement(PrioritizedLookupElement.withPriority(element, 800));
            }
        }
        
        // Add type names
        for (String type : TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("type");
            result.addElement(PrioritizedLookupElement.withPriority(element, 700));
        }
    }
    
    /**
     * Adds completions from variables and functions scanned in the current document.
     */
    private void addScannedCompletions(@NotNull CompletionParameters parameters, 
                                      @NotNull CompletionResultSet result) {
        // This is a simplified implementation
        // A more comprehensive solution would parse the document to find local variables and functions
        
        String documentText = parameters.getEditor().getDocument().getText();
        
        // Track variables we've already found to avoid duplicates
        Set<String> foundVariables = new HashSet<>();
        
        // Scan for variable declarations with var/varip
        Pattern varPattern = Pattern.compile("(?:var|varip)\\s+([a-zA-Z_]\\w*)\\s*=");
        Matcher varMatcher = varPattern.matcher(documentText);
        
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            foundVariables.add(varName);
            LookupElementBuilder element = LookupElementBuilder.create(varName)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText("local var");
            result.addElement(PrioritizedLookupElement.withPriority(element, 600));
        }
        
        // Also scan for direct assignments like "identifier = value"
        // This regex looks for word at start of line or after whitespace, followed by '='
        // and avoids matches for '==', '<=', '>=', '!='
        Pattern assignPattern = Pattern.compile("(?:^|\\s)([a-zA-Z_]\\w*)\\s*=[^=<>!]");
        Matcher assignMatcher = assignPattern.matcher(documentText);
        
        while (assignMatcher.find()) {
            String varName = assignMatcher.group(1);
            // Skip keywords, built-ins, already found vars, and common non-var names
            if (Arrays.asList(KEYWORDS).contains(varName) || 
                Arrays.asList(BUILT_IN_VARIABLES).contains(varName) ||
                Arrays.asList(NAMESPACES).contains(varName) ||
                Arrays.asList(TYPES).contains(varName) ||
                foundVariables.contains(varName) ||
                varName.equals("if") || varName.equals("for")) {
                continue;
            }
            
            foundVariables.add(varName);
            LookupElementBuilder element = LookupElementBuilder.create(varName)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText("local var");
            result.addElement(PrioritizedLookupElement.withPriority(element, 600));
        }
        
        // Very simplified scan for function declarations
        Pattern funcPattern = Pattern.compile("(?:method|function)\\s+([a-zA-Z_]\\w*)\\s*\\(");
        Matcher funcMatcher = funcPattern.matcher(documentText);
        
        while (funcMatcher.find()) {
            String funcName = funcMatcher.group(1);
            LookupElementBuilder element = LookupElementBuilder.create(funcName)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTypeText("local function")
                    .withInsertHandler((ctx, item) -> {
                        // Add parentheses and move cursor inside
                        Editor editor = ctx.getEditor();
                        EditorModificationUtil.insertStringAtCaret(editor, "()");
                        editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                    });
            result.addElement(PrioritizedLookupElement.withPriority(element, 600));
        }
    }
    
    /**
     * Initializes namespace methods for a specific version.
     */
    private static Map<String, String[]> initNamespaceMethodsForVersion(String version, List<String> functionNames) {
        Map<String, String[]> result = new HashMap<>();
        
        // Filter function names that belong to each namespace
        Map<String, List<String>> namespaceMethodsMap = new HashMap<>();
        
        for (String namespace : new String[]{"ta", "math", "array", "str", "color", 
                                             "chart", "strategy", "syminfo", "request", "ticker"}) {
            namespaceMethodsMap.put(namespace, new ArrayList<>());
        }
        
        for (String functionName : functionNames) {
            if (functionName.contains(".")) {
                String[] parts = functionName.split("\\.", 2);
                if (parts.length == 2 && namespaceMethodsMap.containsKey(parts[0])) {
                    namespaceMethodsMap.get(parts[0]).add(parts[1]);
                }
            }
        }
        
        // Convert lists to arrays for the final map
        for (Map.Entry<String, List<String>> entry : namespaceMethodsMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        
        // Add predefined method sets for common namespaces (without trailing parentheses)
        result.put("str", new String[]{
            "length", "format", "tostring", "tonumber", "replace", "replace_all", 
            "lower", "upper", "startswith", "endswith", "contains", "split",
            "substring", "pos", "match"
        });
        
        result.put("math", new String[]{
            "abs", "acos", "asin", "atan", "avg", "ceil", "cos", "exp", 
            "floor", "log", "log10", "max", "min", "pow", "random", 
            "round", "round_to_mintick", "sign", "sin", "sqrt", "sum", 
            "tan", "todegrees", "toradians"
        });
        
        result.put("array", new String[]{
            "new", "new_float", "new_int", "new_bool", "new_string", "new_color",
            "new_line", "new_label", "new_box", "new_table", "new_linefill",
            "get", "set", "push", "pop", "insert", "remove", "clear",
            "size", "copy", "slice", "concat", "fill", "join",
            "min", "max", "avg", "median", "mode", "stdev", "variance",
            "sort", "reverse", "binary_search", "includes", "indexof", "lastindexof",
            "shift", "unshift"
        });
        
        return result;
    }
    
    /**
     * Initializes function parameters map for a specific version.
     */
    private static Map<String, Map<String, String>> initFunctionParametersForVersion(String version) {
        Map<String, Map<String, String>> result = new HashMap<>();
        
        // Strategy functions
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
        strategyEntryParams.put("disable_alert", "bool");
        result.put("strategy.entry", strategyEntryParams);
        
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
        strategyExitParams.put("disable_alert", "bool");
        result.put("strategy.exit", strategyExitParams);
        
        // Indicator parameters
        Map<String, String> indicatorParams = new HashMap<>();
        indicatorParams.put("title", "string");
        indicatorParams.put("shorttitle", "string");
        indicatorParams.put("overlay", "bool");
        indicatorParams.put("format", "const string");
        indicatorParams.put("precision", "int");
        indicatorParams.put("scale", "const scale");
        indicatorParams.put("max_bars_back", "int");
        indicatorParams.put("timeframe", "string");
        result.put("indicator", indicatorParams);
        
        // Plot functions
        Map<String, String> plotParams = new HashMap<>();
        plotParams.put("series", "series");
        plotParams.put("title", "string");
        plotParams.put("color", "color");
        plotParams.put("linewidth", "int");
        plotParams.put("style", "const plot_style");
        plotParams.put("transp", "int");
        plotParams.put("histbase", "float");
        plotParams.put("offset", "int");
        plotParams.put("join", "bool");
        plotParams.put("editable", "bool");
        plotParams.put("show_last", "int");
        result.put("plot", plotParams);
        
        // More function parameters can be added here
        
        return result;
    }

    // Define a class to handle auto-popup for parameter completion
    private static class CompletionAutoPopupHandler {
        public static void install(Disposable disposable) {
            EditorActionManager actionManager = EditorActionManager.getInstance();
            TypedAction typedAction = actionManager.getTypedAction();
            TypedActionHandler oldHandler = typedAction.getRawHandler();
            
            typedAction.setupRawHandler(new TypedActionHandler() {
                @Override
                public void execute(@NotNull Editor editor, char c, @NotNull DataContext dataContext) {
                    if (oldHandler != null) {
                        oldHandler.execute(editor, c, dataContext);
                    }
                    
                    // Trigger completion popup for parentheses or comma
                    if (c == '(' || c == ',') {
                        Project project = editor.getProject();
                        if (project != null) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                            });
                        }
                    }
                }
            });
        }
    }
} 