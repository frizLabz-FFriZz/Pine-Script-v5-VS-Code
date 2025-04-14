package com.pinescript.plugin.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.pinescript.plugin.completion.handlers.SmartInsertHandler;
import com.pinescript.plugin.language.PineScriptLanguage;
import com.pinescript.plugin.language.PineScriptIcons;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

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
    // Add new maps for storing type information
    private static final Map<String, Map<String, String>> FUNCTION_RETURN_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> VARIABLE_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> CONSTANT_TYPES = new ConcurrentHashMap<>();

    // Default to version 5 if version is not specified
    private static final String DEFAULT_VERSION = "5";
    
    // Pattern to match version in Pine Script files
    private static final Pattern VERSION_PATTERN = Pattern.compile("//@version=(\\d+)");

    /**
     * Checks if the cursor is inside a string literal.
     * @param text The document text
     * @param offset The cursor offset
     * @return true if the cursor is inside a string literal, false otherwise
     */
    private static boolean isInsideString(String text, int offset) {
        LOG.info("Checking if cursor at offset " + offset + " is inside string");
        // Get the current line.
        int lineStart = text.lastIndexOf('\n', offset - 1);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        int lineEnd = text.indexOf('\n', offset);
        lineEnd = (lineEnd == -1) ? text.length() : lineEnd;
        String currentLine = text.substring(lineStart, lineEnd);

        // Count unescaped quotes in the current line up to the offset.
        int relativeOffset = offset - lineStart;
        int quoteCount = 0;
        char currentQuote = '\0';
        for (int i = 0; i < relativeOffset; i++) {
            char c = currentLine.charAt(i);
            if (c == '"' || c == '\'') {
                // Check if not escaped.
                if (i == 0 || currentLine.charAt(i - 1) != '\\') {
                    // Toggle in-string state if matching the same quote.
                    if (! (currentQuote == c)) {
                        currentQuote = c;
                        quoteCount++;
                    } else {
                        currentQuote = '\0';
                        quoteCount++;
                    }
                }
            }
        }
        // If there's an odd number of unescaped quotes, we're inside a string.
        return (quoteCount % 2 == 1);
    }

    // Static initialization to load definitions for default version on startup
    static {
        try {
            LOG.info("PineScriptCompletionContributor static initialization starting...");
            if (DEFAULT_VERSION != null) {
                loadDefinitionsForVersion(DEFAULT_VERSION);
                LOG.info("Successfully loaded definitions for default version: " + DEFAULT_VERSION);
            } else {
                LOG.warn("DEFAULT_VERSION is null in static initializer, skipping definition loading");
            }
        } catch (Exception e) {
            LOG.error("Error during static initialization", e);
            // Continue initialization rather than failing entirely
        }
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
        super();
        LOG.info("PineScriptCompletionContributor initialized");
        
        // Install custom completion handler to ensure parameter info and dot completion works correctly
        CompletionAutoPopupHandler.install(ApplicationManager.getApplication());
        
        // Standard extension for completions
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
                       
                       // Explicitly write to IDE logs for diagnostics
                       LOG.warn("PineScriptCompletionContributor running at offset " + offset);
                       
                       // Skip autocompletion if inside a string
                       if (isInsideString(documentText, offset)) {
                           LOG.info("Cursor is inside a string, skipping autocompletion");
                           return;
                       }
                       
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
                               LOG.warn("🔍 [addCompletions] DOT DETECTED at end of text before cursor");
                               String namespace = findNamespaceBeforeDot(textBeforeCursor);
                               LOG.warn("🔍 [addCompletions] Namespace detected before dot: '" + namespace + "'");
                               
                               if (namespace != null) {
                                   // Check if this is a known built-in namespace or a custom namespace
                                   boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(namespace);
                                   
                                   // Get the sets for this version
                                   Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                                   Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                                   Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                                   
                                   // Detailed logging of available definitions for debugging
                                   LOG.warn("🔍 [addCompletions] Checking namespace '" + namespace + "': " +
                                            "is built-in=" + isBuiltInNamespace + 
                                            ", functions=" + functions.size() + 
                                            ", variables=" + variables.size() + 
                                            ", constants=" + constants.size());
                                   
                                   // Check if this is a custom namespace
                                   boolean isCustomNamespace = false;
                                   
                                   // Check if any definitions start with namespace + "."
                                   List<String> matchingDefs = new ArrayList<>();
                                   for (String definition : CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>())) {
                                       if (definition.startsWith(namespace + ".")) {
                                           isCustomNamespace = true;
                                           matchingDefs.add(definition);
                                       }
                                   }
                                   
                                   LOG.warn("🔍 [addCompletions] Namespace '" + namespace + "' status: " + 
                                            "built-in=" + isBuiltInNamespace + 
                                            ", custom=" + isCustomNamespace + 
                                            ", matching definitions=" + matchingDefs.size());
                                   
                                   if (matchingDefs.size() < 20 && matchingDefs.size() > 0) {
                                       LOG.warn("🔍 [addCompletions] Matching definitions: " + matchingDefs);
                                   }
                                   
                                   if (isBuiltInNamespace || isCustomNamespace) {
                                       LOG.warn("🔍 [addCompletions] VALID NAMESPACE! Calling addNamespaceMethodCompletions for: " + namespace);
                                       addNamespaceMethodCompletions(result, namespace, version);
                                       
                                       // Count how many items were added
                                       try {
                                           java.lang.reflect.Field field = result.getClass().getDeclaredField("myItems");
                                           field.setAccessible(true);
                                           Object itemsObj = field.get(result);
                                           if (itemsObj instanceof Collection) {
                                               int itemCount = ((Collection<?>) itemsObj).size();
                                               LOG.warn("🔍 [addCompletions] Result now contains " + itemCount + " items after adding namespace methods");
                                               
                                               // If no items, try to add some debugging suggestions
                                               if (itemCount == 0) {
                                                   LOG.warn("🔍 [addCompletions] NO ITEMS FOUND! Adding debugging suggestions");
                                                   LookupElementBuilder element = LookupElementBuilder.create("debug_item")
                                                           .withIcon(AllIcons.Nodes.Method)
                                                           .withTypeText("debug item");
                                                   result.addElement(PrioritizedLookupElement.withPriority(element, 1000));
                                                   
                                                   // Check if we still have zero items - if so, the result might be broken
                                                   field = result.getClass().getDeclaredField("myItems");
                                                   field.setAccessible(true);
                                                   itemsObj = field.get(result);
                                                   if (itemsObj instanceof Collection) {
                                                       itemCount = ((Collection<?>) itemsObj).size();
                                                       LOG.warn("🔍 [addCompletions] After adding debug item, result now contains " + itemCount + " items");
                                                   }
                                               }
                                           }
                                       } catch (Exception e) {
                                           LOG.warn("🔍 [addCompletions] Error checking result size: " + e.getMessage());
                                       }
                                       
                                       LOG.warn("🔍 [addCompletions] RETURNING after handling namespace methods");
                                       return; // Stop processing after handling namespace methods
                                   } else {
                                       LOG.warn("🔍 [addCompletions] Namespace '" + namespace + "' is not recognized as built-in or custom");
                                   }
                               } else {
                                   LOG.warn("🔍 [addCompletions] No namespace detected before dot");
                               }
                           }
                           
                           // Special handling for function parameters - detect if inside parentheses
                           checkFunctionCallContext(documentText, offset);
                           
                           if (isInsideFunctionCall && currentFunctionName != null) {
                               LOG.warn("🔍 [addCompletions] Inside function call: " + currentFunctionName + ", parameter index: " + currentParamIndex);
                               
                               // Check if we're after a parameter name with equals sign (named parameter assignment)
                               boolean isInNamedParameterValue = false;
                               String parameterName = null;
                               
                               // Extract the text for the current parameter
                               int lastOpenParenPos = documentText.lastIndexOf('(');
                               int lastCommaPos = documentText.lastIndexOf(',');
                               int startPos = Math.max(lastOpenParenPos, lastCommaPos);
                               
                               if (startPos >= 0 && startPos < documentText.length()) {
                                   String parameterText = documentText.substring(startPos + 1).trim();
                                   LOG.warn("🔍 [addCompletions] Parameter text: '" + parameterText + "'");
                                   
                                   // Check if this is a named parameter (contains equals sign)
                                   int equalsPos = parameterText.indexOf('=');
                                   if (equalsPos >= 0) {
                                       isInNamedParameterValue = true;
                                       parameterName = parameterText.substring(0, equalsPos).trim();
                                       LOG.warn("🔍 [addCompletions] Named parameter detected: '" + parameterName + "'");
                                   }
                               }
                               
                               // Get function parameter information
                               String fullFunctionName = currentFunctionName;
                               
                               if (isInNamedParameterValue && parameterName != null) {
                                   // Get the type of the named parameter
                                   String paramType = null;
                                   
                                   // Get parameter list for this function
                                   Map<String, List<Map<String, String>>> functionArgs = FUNCTION_ARGUMENTS_CACHE.getOrDefault(version, new HashMap<>());
                                   List<Map<String, String>> args = functionArgs.get(fullFunctionName);
                                   
                                   if (args != null) {
                                       for (Map<String, String> arg : args) {
                                           if (parameterName.equals(arg.get("name"))) {
                                               paramType = arg.get("type");
                                               break;
                                           }
                                       }
                                   }
                                   
                                   if (paramType != null) {
                                       LOG.warn("🔍 [addCompletions] Found parameter type for named parameter: " + paramType);
                                       addCompletionsForExpectedType(result, paramType, version);
                                   } else {
                                       LOG.warn("🔍 [addCompletions] Could not determine type for named parameter: " + parameterName);
                                       addAllCompletionItems(result, version);
                                   }
                               } else {
                                   // Add parameter name suggestions
                                   LOG.warn("🔍 [addCompletions] Adding parameter suggestions for function: " + fullFunctionName);
                                   addParameterCompletions(result, fullFunctionName, currentParamIndex, version);
                               }
                               
                               // After adding parameter suggestions, return to prevent adding other items
                               return;
                           }
                           
                           // Detect assignment statement and infer expected type
                           String expectedType = inferExpectedType(documentText.substring(0, offset));
                           
                           if (expectedType != null) {
                               LOG.info("Inferred expected type in assignment: " + expectedType);
                               addCompletionsForExpectedType(result, expectedType, version);
                           } else {
                               // Fall back to processing standard completions
                               LOG.info("No expected type inferred, adding standard completions");
                               processStandardCompletions(parameters, result, version);
                           }
                       } else {
                           // At beginning of document, add standard completions
                           processStandardCompletions(parameters, result, version);
                       }
                   }
               });
    }
    
    /**
     * Override fillCompletionVariants to ensure completions are called
     */
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        // Log when completion is triggered
        LOG.warn("🔍 [fillCompletionVariants] CALLED - type: " + parameters.getCompletionType() + 
                 ", invocation count: " + parameters.getInvocationCount());
        
        Document document = parameters.getEditor().getDocument();
        int offset = parameters.getOffset();
        
        // Get document text
        String text = document.getText();
        
        // Define textBeforeCursor once for use throughout the method
        String textBeforeCursor = text.substring(0, offset);
        
        // Check if we're typing at the beginning of a line
        int lastNewlinePos = textBeforeCursor.lastIndexOf('\n');
        if (lastNewlinePos != -1) {
            String afterNewline = textBeforeCursor.substring(lastNewlinePos + 1).trim();
            // If there's only alphanumeric/underscore characters after the last newline, it's likely a variable/function name
            if (afterNewline.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                LOG.warn("🔍 [fillCompletionVariants] Detected typing at beginning of line: '" + afterNewline + "'");
                
                // For identifiers at the beginning of a line, prioritize namespaces
                String version = detectPineScriptVersion(text);
                
                // Create a prefixed completion result
                CompletionResultSet prefixResult = result.withPrefixMatcher(afterNewline);
                
                // First add namespaces for better prioritization - they should appear at the top
                        for (String namespace : NAMESPACES) {
                    LookupElementBuilder element = LookupElementBuilder.create(namespace)
                                .withTypeText("namespace")
                            .withIcon(AllIcons.Nodes.Package)
                            .withInsertHandler((ctx, item) -> {
                                Editor editor = ctx.getEditor();
                                editor.getDocument().insertString(ctx.getTailOffset(), ".");
                                editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                                
                                // Schedule popup for namespace members
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    if (editor.isDisposed()) return;
                                    AutoPopupController.getInstance(ctx.getProject()).autoPopupMemberLookup(editor, null);
                                });
                            });
                    
                    // Add with high priority
                    prefixResult.addElement(PrioritizedLookupElement.withPriority(element, 1000));
                }
                
                // Add scanned completions (local variables) - pass expected type
                String expectedType = null;
                // Check if we're in a variable assignment context
                if (textBeforeCursor.contains("=")) {
                    expectedType = inferExpectedType(textBeforeCursor);
                    LOG.warn("🔍 [fillCompletionVariants] Expected type for variable assignment: '" + expectedType + "'");
                }
                addScannedCompletions(parameters, prefixResult, expectedType);
                
                // Add all standard completions
                addAllCompletionItems(prefixResult, version);
                
                return;
            }
        }
        
        // Log specifically for dot completion
        if (offset > 0) {
            char prevChar = text.charAt(offset - 1);
            
            // Case 1: Text ends with a dot - standard dot completion
            if (prevChar == '.') {
                // Reuse the already defined textBeforeCursor variable
                String namespace = findNamespaceBeforeDot(textBeforeCursor);
                LOG.warn("🔍 [fillCompletionVariants] DOT DETECTED! Namespace before dot: '" + namespace + "'");
                
                // Check if this is a valid namespace
                if (namespace != null) {
                    boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(namespace);
                    LOG.warn("🔍 [fillCompletionVariants] Is built-in namespace: " + isBuiltInNamespace);
                    
                    // Check for custom namespace
                    String version = detectPineScriptVersion(text);
                    Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                    Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                    Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                    
                    boolean hasMatchingDefinition = false;
                    for (String def : functions) {
                        if (def.startsWith(namespace + ".")) {
                            hasMatchingDefinition = true;
                            LOG.warn("🔍 [fillCompletionVariants] Found matching function definition: " + def);
                            break;
                        }
                    }
                    
                    if (!hasMatchingDefinition) {
                        for (String def : variables) {
                            if (def.startsWith(namespace + ".")) {
                                hasMatchingDefinition = true;
                                LOG.warn("🔍 [fillCompletionVariants] Found matching variable definition: " + def);
                                break;
                            }
                        }
                    }
                    
                    if (!hasMatchingDefinition) {
                        for (String def : constants) {
                            if (def.startsWith(namespace + ".")) {
                                hasMatchingDefinition = true;
                                LOG.warn("🔍 [fillCompletionVariants] Found matching constant definition: " + def);
                                break;
                            }
                        }
                    }
                    
                    LOG.warn("🔍 [fillCompletionVariants] Has matching definitions for namespace: " + hasMatchingDefinition);
                    
                    // If we have a valid namespace with members, add them directly
                    if (isBuiltInNamespace || hasMatchingDefinition) {
                        LOG.warn("🔍 [fillCompletionVariants] Adding namespace method completions");
                        addNamespaceMethodCompletions(result, namespace, version);
                        return; // Skip standard completion
                    }
                }
            }
            // Case 2: We're after a dot with some text already (e.g., "request.sec")
            else {
                // Reuse existing textBeforeCursor variable
                int lastDotPos = textBeforeCursor.lastIndexOf('.');
                
                // Check if we have a dot in the text and there's text after it
                if (lastDotPos >= 0 && lastDotPos < textBeforeCursor.length() - 1) {
                    // Rather than using a hacky distance check, validate the context properly:
                    // 1. Check if we're still in an identifier context after the dot
                    // 2. If we encounter any delimiter or non-identifier character after the dot,
                    //    we're no longer in a namespace completion context
                    
                    // Get the text after the last dot
                    String textAfterDot = textBeforeCursor.substring(lastDotPos + 1);
                    
                    // Check if we've moved beyond the identifier context by looking for delimiters/operators
                    boolean isStillInMemberContext = true;
                    
                    // Rather than listing characters that break the context, check if all characters
                    // after the dot match valid identifier characters (letters, digits, underscore)
                    Pattern validIdentifierPattern = Pattern.compile("^[a-zA-Z0-9_]+$");
                    if (!validIdentifierPattern.matcher(textAfterDot).matches()) {
                        isStillInMemberContext = false;
                        LOG.warn("🔍 [fillCompletionVariants] Not in member context anymore, found non-identifier character in: '" + textAfterDot + "'");
                    }
                    
                    // Only process namespace completion if we're still in a member access context
                    if (isStillInMemberContext) {
                        // Check if we're inside a comment
                        boolean insideComment = isInsideComment(textBeforeCursor, lastDotPos);
                        if (insideComment) {
                            LOG.warn("🔍 [fillCompletionVariants] Dot is inside a comment, skipping namespace completion");
                        } else {
                            String potentialNamespace = findNamespaceAtPosition(textBeforeCursor, lastDotPos);
                            String partialText = textBeforeCursor.substring(lastDotPos + 1);
                            
                            LOG.warn("🔍 [fillCompletionVariants] Found dot with text after it. " +
                                   "Potential namespace: '" + potentialNamespace + "', " +
                                   "Partial text: '" + partialText + "'");
                            
                            if (potentialNamespace != null) {
                                boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(potentialNamespace);
                                String version = detectPineScriptVersion(text);
                                
                                // Check if this is a valid namespace with members
                                boolean hasMatchingDefinition = false;
                                Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                                Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                                Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                                
                                for (String def : functions) {
                                    if (def.startsWith(potentialNamespace + ".")) {
                                        hasMatchingDefinition = true;
                                        break;
                                    }
                                }
                                
                                if (!hasMatchingDefinition) {
                                    for (String def : variables) {
                                        if (def.startsWith(potentialNamespace + ".")) {
                                            hasMatchingDefinition = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (!hasMatchingDefinition) {
                                    for (String def : constants) {
                                        if (def.startsWith(potentialNamespace + ".")) {
                                            hasMatchingDefinition = true;
                                            break;
                                        }
                                    }
                                }
                                
                                // For valid namespaces, add members with a custom prefix matcher
                                if (isBuiltInNamespace || hasMatchingDefinition) {
                                    LOG.warn("🔍 [fillCompletionVariants] Creating filtered result set with prefix: '" + partialText + "'");
                                    
                                    // Create a filtered result set with the prefix to match
                                    CompletionResultSet filteredResult = result.withPrefixMatcher(partialText);
                                    LOG.warn("🔍 [fillCompletionVariants] Created filtered result set for: '" + partialText + "'");
                                    
                                    // Add namespace method completions with this prefix matcher
                                    addNamespaceMethodCompletions(filteredResult, potentialNamespace, version);
                                    return; // Skip standard completion
                                }
                            }
                        }
                    } else {
                        LOG.warn("🔍 [fillCompletionVariants] Cursor is no longer in a namespace member completion context");
                    }
                }
            }

            // Case 3: Check for variable assignment with = operator
            int equalsPos = textBeforeCursor.lastIndexOf("=");
            if (equalsPos >= 0 && equalsPos < textBeforeCursor.length() - 1) {
                try {
                    // Check that this is a standalone equals, not part of ==, >=, etc.
                    if (equalsPos == 0 || (equalsPos > 0 && 
                        textBeforeCursor.charAt(equalsPos - 1) != ':' && 
                        textBeforeCursor.charAt(equalsPos - 1) != '=' && 
                        textBeforeCursor.charAt(equalsPos - 1) != '<' && 
                        textBeforeCursor.charAt(equalsPos - 1) != '>' && 
                        textBeforeCursor.charAt(equalsPos - 1) != '!')) {
                        
                        // Extract text between start of line/statement and the = operator
                        String lineText = textBeforeCursor;
                        int lineStart = Math.max(lineText.lastIndexOf('\n'), lineText.lastIndexOf(';'));
                        
                        // Ensure lineStart is valid (it might be -1 if not found)
                        lineStart = Math.max(lineStart, -1); // If both \n and ; are not found, use -1
                        
                        // Check that lineStart is less than equalsPos to avoid invalid range
                        if (lineStart < equalsPos) {
                            if (lineStart >= 0) {
                                lineText = lineText.substring(lineStart + 1, equalsPos).trim();
                            } else {
                                lineText = lineText.substring(0, equalsPos).trim();
                            }
                            
                            LOG.warn("🔍 [fillCompletionVariants] Found variable assignment: '" + lineText + " ='");
                            
                            // Try to infer the expected type
                            String expectedType = inferExpectedType(textBeforeCursor);
                            String version = detectPineScriptVersion(text);
                            
                            if (expectedType != null) {
                                LOG.warn("🔍 [fillCompletionVariants] Expected type for '" + lineText + "' is '" + expectedType + "'");
                                
                                // Get text after = for prefix matching
                                String textAfterEquals = textBeforeCursor.substring(equalsPos + 1).trim();
                                LOG.warn("🔍 [fillCompletionVariants] Text after = is: '" + textAfterEquals + "'");
                                
                                // Check if the user is typing a namespace
                                int lastDotInEquals = textAfterEquals.lastIndexOf('.');
                                if (lastDotInEquals > 0) {
                                    String potentialNamespace = textAfterEquals.substring(0, lastDotInEquals);
                                    String textAfterDot = textAfterEquals.substring(lastDotInEquals + 1);
                                    
                                    LOG.warn("🔍 [fillCompletionVariants] Potential namespace in equals: '" + potentialNamespace + "', after dot: '" + textAfterDot + "'");
                                    
                                    // Check if this is a valid namespace
                                    boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(potentialNamespace);
                                    boolean hasMatchingDefinition = false;
                                    
                                    Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                                    Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                                    Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                                    
                                    for (String def : functions) {
                                        if (def.startsWith(potentialNamespace + ".")) {
                                            hasMatchingDefinition = true;
                                            break;
                                        }
                                    }
                                    
                                    if (!hasMatchingDefinition) {
                                        for (String def : variables) {
                                            if (def.startsWith(potentialNamespace + ".")) {
                                                hasMatchingDefinition = true;
                                                break;
                                            }
                                        }
                                    }
                                    
                                    if (!hasMatchingDefinition) {
                                        for (String def : constants) {
                                            if (def.startsWith(potentialNamespace + ".")) {
                                                hasMatchingDefinition = true;
                                                break;
                                            }
                                        }
                                    }
                                    
                                    if (isBuiltInNamespace || hasMatchingDefinition) {
                                        // Create a filtered result set with the prefix to match
                                        CompletionResultSet filteredResult = result.withPrefixMatcher(textAfterDot);
                                        
                                        // Add namespace method completions
                                        addNamespaceMethodCompletions(filteredResult, potentialNamespace, version);
                                        return; // Skip standard completion
                                    }
                                }
                                
                                // Create a filtered result set with the prefix to match
                                CompletionResultSet filteredResult = result.withPrefixMatcher(textAfterEquals);
                                
                                // First add local variables from the current document
                                // Pass the expected type to the scanned completions
                                addScannedCompletions(parameters, filteredResult, expectedType);
                                
                                // Add type-specific completions from standard libraries
                                addCompletionsForExpectedType(filteredResult, expectedType, version);
                                return; // Skip standard completion
                            }
                        }
                    }
                } catch (StringIndexOutOfBoundsException e) {
                    LOG.warn("🔍 [fillCompletionVariants] StringIndexOutOfBoundsException in variable assignment: " + e.getMessage());
                    // Continue with standard completion
                }
            }
        }
        
        // Check if we're inside a function call - this should be checked before falling back to standard completions
        checkFunctionCallContext(text, offset);
        
        if (isInsideFunctionCall && currentFunctionName != null) {
            LOG.warn("🔍 [fillCompletionVariants] Inside function call: " + currentFunctionName + ", param index: " + currentParamIndex);
            
            // Add parameter completions
            String version = detectPineScriptVersion(text);
            
            // Modify prefixMatcher based on text after last comma
            String textAfterComma = "";
            
            // Find the opening parenthesis of the current function call
            int openParenCount = 0;
            int closeParenCount = 0;
            int currentFunctionOpenPos = -1;
            
            // Scan backward to find the opening parenthesis of the current function
            for (int i = textBeforeCursor.length() - 1; i >= 0; i--) {
                char c = textBeforeCursor.charAt(i);
                if (c == ')') {
                    closeParenCount++;
                } else if (c == '(') {
                    openParenCount++;
                    if (openParenCount > closeParenCount) {
                        // We've found the opening parenthesis of our current function call
                        currentFunctionOpenPos = i;
                        break;
                    }
                }
            }
            
            if (currentFunctionOpenPos >= 0) {
                // Now look for the last comma, but only after the opening parenthesis of current function
                int lastCommaPos = textBeforeCursor.lastIndexOf(',', textBeforeCursor.length() - 1);
                
                if (lastCommaPos > currentFunctionOpenPos) {
                    // If we found a comma after the opening parenthesis, get text after it
                    textAfterComma = textBeforeCursor.substring(lastCommaPos + 1).trim();
                    LOG.debug("Found comma in current function at position " + lastCommaPos + 
                             ", text after: '" + textAfterComma + "'");
                } else {
                    // No comma found in this function call, use everything after the opening parenthesis
                    textAfterComma = textBeforeCursor.substring(currentFunctionOpenPos + 1).trim();
                    LOG.debug("No comma in current function, using text after opening parenthesis: '" + 
                             textAfterComma + "'");
                }
            }
            
            LOG.warn("🔍 [fillCompletionVariants] Text after comma/paren: '" + textAfterComma + "'");
            
            // Check if there's a dot after the comma, which might indicate namespace access
            int dotAfterComma = textAfterComma.lastIndexOf('.');
            if (dotAfterComma >= 0) {
                String namespaceInParam = textAfterComma.substring(0, dotAfterComma).trim();
                String afterDot = textAfterComma.substring(dotAfterComma + 1).trim();
                
                LOG.warn("🔍 [fillCompletionVariants] Potential namespace in parameter: '" + namespaceInParam + "', after dot: '" + afterDot + "'");
                
                boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(namespaceInParam);
                if (isBuiltInNamespace) {
                    CompletionResultSet filteredResult = result.withPrefixMatcher(afterDot);
                    addNamespaceMethodCompletions(filteredResult, namespaceInParam, version);
                    return;
                }
            }
            
            // Prepare a filtered result set based on the text after comma
            CompletionResultSet filteredResult = result.withPrefixMatcher(textAfterComma);
            
            // Add parameter completions
            addParameterCompletions(filteredResult, currentFunctionName, currentParamIndex, version);
            
            // For function parameters, also look for local variables that might match
            // Pass null as expectedType since we handle type filtering separately for function params
            addScannedCompletions(parameters, filteredResult, null);
            
            return; // Skip standard completion
        }
        
        // Call the parent implementation to handle the standard completion
        LOG.warn("🔍 [fillCompletionVariants] Calling super.fillCompletionVariants");
        super.fillCompletionVariants(parameters, result);
        LOG.warn("🔍 [fillCompletionVariants] Returned from super.fillCompletionVariants");
        
        // Add local variables and functions from the document
        String expectedType = null;
        // Check if we're in a variable assignment context
        if (textBeforeCursor.contains("=")) {
            expectedType = inferExpectedType(textBeforeCursor);
            LOG.warn("🔍 [fillCompletionVariants] Expected type for standard completions: '" + expectedType + "'");
        }
        addScannedCompletions(parameters, result, expectedType);
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
     * Loads Pine Script definitions for a specific version
     * @param version The Pine Script version
     */
    private static synchronized void loadDefinitionsForVersion(String version) {
        try {
            if (version == null) {
                LOG.warn("Attempted to load definitions for null version, using DEFAULT_VERSION");
                version = DEFAULT_VERSION;
                
                // If default version is also null, use a fallback
                if (version == null) {
                    LOG.warn("DEFAULT_VERSION is also null, using fallback version '5'");
                    version = "5";
                }
            }
            
            LOG.info("Loading definitions for Pine Script version " + version);
            
            // If already loaded, skip
            if (CACHED_DEFINITIONS.containsKey(version)) {
                LOG.info("Definitions for version " + version + " already loaded");
                return;
            }
            
            List<String> functions = loadNamesFromDefinitionFile(version, "functions.json");
            List<String> variables = loadNamesFromDefinitionFile(version, "variables.json");
            List<String> constants = loadNamesFromDefinitionFile(version, "constants.json");
            
            Map<String, List<Map<String, String>>> functionArguments = loadFunctionArguments(version);
            Map<String, String> returnTypes = loadReturnTypesFromDefinitionFile(version, "functions.json");
            Map<String, String> variableTypes = loadTypesFromDefinitionFile(version, "variables.json");
            Map<String, String> constantTypes = loadTypesFromDefinitionFile(version, "constants.json");
            
            // Store all definitions together for general lookup, plus type-specific collections
            List<String> allDefinitions = new ArrayList<>();
            allDefinitions.addAll(functions);
            allDefinitions.addAll(variables);
            allDefinitions.addAll(constants);
            
            Set<String> functionsSet = new HashSet<>(functions);
            Set<String> variablesSet = new HashSet<>(variables);
            Set<String> constantsSet = new HashSet<>(constants);
            
            CACHED_DEFINITIONS.put(version, allDefinitions);
            FUNCTIONS_MAP.put(version, functionsSet);
            VARIABLES_MAP.put(version, variablesSet);
            CONSTANTS_MAP.put(version, constantsSet);
            
            FUNCTION_ARGUMENTS_CACHE.put(version, functionArguments);
            FUNCTION_RETURN_TYPES.put(version, returnTypes);
            VARIABLE_TYPES.put(version, variableTypes);
            CONSTANT_TYPES.put(version, constantTypes);
            
            // Initialize namespace methods
            NAMESPACE_METHODS_CACHE.put(version, initNamespaceMethodsForVersion(version, functions));
            
            // Initialize function parameters
            FUNCTION_PARAMETERS_CACHE.put(version, initFunctionParametersForVersion(version));
            
            LOG.info("Successfully loaded definitions for Pine Script version " + version);
        } catch (Exception e) {
            LOG.error("Error loading definitions for version " + version, e);
            
            // Create empty maps/sets if they don't exist yet to prevent further errors
            CACHED_DEFINITIONS.putIfAbsent(version, new ArrayList<>());
            FUNCTIONS_MAP.putIfAbsent(version, new HashSet<>());
            VARIABLES_MAP.putIfAbsent(version, new HashSet<>());
            CONSTANTS_MAP.putIfAbsent(version, new HashSet<>());
            FUNCTION_ARGUMENTS_CACHE.putIfAbsent(version, new HashMap<>());
            FUNCTION_RETURN_TYPES.putIfAbsent(version, new HashMap<>());
            VARIABLE_TYPES.putIfAbsent(version, new HashMap<>());
            CONSTANT_TYPES.putIfAbsent(version, new HashMap<>());
            NAMESPACE_METHODS_CACHE.putIfAbsent(version, new HashMap<>());
            FUNCTION_PARAMETERS_CACHE.putIfAbsent(version, new HashMap<>());
        }
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
     * Attempts to find a potential namespace identifier before a dot
     * For example, in "request.", this function would return "request"
     */
    private static String findNamespaceBeforeDot(String text) {
        if (text == null || text.isEmpty() || !text.endsWith(".")) {
            return null;
        }
        
        // Find the last non-identifier character before the final dot
        int lastNonWordChar = -1;
        
        // Iterate backwards from just before the dot
        for (int i = text.length() - 2; i >= 0; i--) {
            char c = text.charAt(i);
            // If we find a character that's not a letter, digit, or underscore
            if (!Character.isJavaIdentifierPart(c)) {
                lastNonWordChar = i;
                break;
            }
        }
        
        // Extract the word between the last non-word character and the dot
        String word = text.substring(lastNonWordChar + 1, text.length() - 1);
        LOG.warn("🔍 [findNamespaceBeforeDot] Found namespace before dot: \"" + word + "\"");
        
        // Validate that this is a proper identifier
        if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) {
            LOG.warn("🔍 [findNamespaceBeforeDot] Invalid namespace: \"" + word + "\"");
            return null;
        }
        
        return word;
    }
    
    /**
     * Determines if the cursor is within a function call.
     */
    private static boolean isInsideFunctionCall(String text, int offset) {
        // Skip this check if we're in a variable declaration with type
        String textToCheck = text.substring(0, offset);
        
        // Check if we're just typing a new identifier at the beginning of a line
        int lastNewlinePos = textToCheck.lastIndexOf('\n');
        if (lastNewlinePos != -1) {
            String afterNewline = textToCheck.substring(lastNewlinePos + 1).trim();
            // If there's only alphanumeric/underscore characters after the last newline, it's likely a variable/function name
            if (afterNewline.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                LOG.warn(">>>>>> NOT A FUNCTION CALL: Just typing identifier at beginning of line");
                return false;
            }
        }
        
        // Check for variable declaration patterns that should NOT trigger function detection
        Pattern varDeclarationPattern = Pattern.compile("(?:var|varip)\\s+(?:[a-zA-Z_]\\w*)\\s+(?:[a-zA-Z_]\\w*)\\s*=");
        Matcher varDeclMatcher = varDeclarationPattern.matcher(textToCheck);
        boolean isVarDeclaration = false;
        while (varDeclMatcher.find()) {
            // If the last match is close to the cursor, we're likely in a variable declaration
            if (varDeclMatcher.end() > offset - 20) {
                isVarDeclaration = true;
                LOG.warn(">>>>>> NOT A FUNCTION CALL: Detected variable declaration with type");
            }
        }
        
        if (isVarDeclaration) {
            return false;
        }
        
        // Simple check: look for an open parenthesis that's not closed
        int openCount = 0;
        int closeCount = 0;
        
        // Track the position of the most recent relevant opening parenthesis
        int lastOpenParenPos = -1;
        
        for (int i = 0; i < text.length() && i < offset; i++) {
            char c = text.charAt(i);
            if (c == '(') {
                openCount++;
                lastOpenParenPos = i;
            }
            else if (c == ')') {
                closeCount++;
            }
        }
        
        // We're in a function call if there are unclosed parentheses
        boolean inFunction = openCount > closeCount;
        
        // If it looks like we're in a function, do additional verification
        if (inFunction && lastOpenParenPos > 0) {
            // Check if there's an identifier before the parenthesis
            for (int i = lastOpenParenPos - 1; i >= 0; i--) {
                char c = text.charAt(i);
                
                // Skip whitespace
                if (Character.isWhitespace(c)) {
                    continue;
                }
                
                // If we found an identifier character (or dot for method calls)
                if (Character.isJavaIdentifierPart(c) || c == '.') {
                    LOG.warn(">>>>>> DETECTED FUNCTION CALL: Cursor is inside a function call");
                    return true;
                } else {
                    // Not a valid function call character - might be a conditional or something else
                    break;
                }
            }
        }
        
        return inFunction;
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
        Document document = parameters.getEditor().getDocument();
        int offset = parameters.getOffset();
        String documentText = document.getText();
        
        // Initialize with empty values
        isInsideFunctionCall = false;
        currentFunctionName = null;
        currentParamIndex = 0;

        // Then add context-specific completions
        if (isInsideFunctionCall(documentText, offset)) {
            LOG.info("🔍 [processStandardCompletions] In function call context");
            currentFunctionName = extractFunctionName(documentText.substring(0, offset));
            
            if (currentFunctionName != null) {
                currentParamIndex = getParamIndexFromText(documentText.substring(0, offset));
                LOG.info("🔍 [processStandardCompletions] Found function: " + currentFunctionName + ", param index: " + currentParamIndex);
                
                // Add parameter-specific completions
                addParameterCompletions(result, currentFunctionName, currentParamIndex, version);
                return; // Early return for function parameters
            }
        }
        
        // Always add all completions first
        addAllCompletionItems(result, version);
        
        if (isInVariableDeclaration(documentText, offset)) {
            LOG.info("🔍 [processStandardCompletions] In variable declaration context");
            String expectedType = inferExpectedType(documentText.substring(0, offset));
            if (expectedType != null) {
                addCompletionsForExpectedType(result, expectedType, version);
            }
        }
    }

    private boolean isInVariableDeclaration(String text, int offset) {
        // Add logic to detect if we're in a variable declaration context
        return text.substring(0, offset).matches(".*(?:var|varip)\\s+(?:[a-zA-Z_]\\w*)\\s+$");
    }

    
    // Implementation of addCompletionsForExpectedType was removed from here to resolve duplicate method error
    // The implementation can be found around line ~1413
    /**
     * Infers the expected type from the left-hand side of an assignment
     * @param textBeforeCursor The text before the cursor position
     * @return The expected type, or null if it cannot be determined
     */
    private String inferExpectedType(String textBeforeCursor) {
        LOG.warn("🚨 [DEBUG] Starting Type Inference - Text before cursor: '" + 
                 (textBeforeCursor.length() > 30 ? "..." + textBeforeCursor.substring(textBeforeCursor.length() - 30) : textBeforeCursor) + "'");
        
        // First, check for variable declaration with explicit type and qualifiers
        // Patterns to match:
        // 1. var TYPE varName =  (var qualifier)
        // 2. varip TYPE varName =  (varip qualifier)
        // 3. series TYPE varName =  (series qualifier)
        // 4. simple TYPE varName =  (simple qualifier)
        // 5. const TYPE varName =  (const qualifier)
        
        // Match pattern: <qualifier> <type> <varName> =
        Pattern typedVarPattern = Pattern.compile(
            "(?:(var|varip|series|simple|const)\\s+)?([a-zA-Z_]\\w*)\\s+([a-zA-Z_]\\w*)\\s*="
        );
        Matcher typedVarMatcher = typedVarPattern.matcher(textBeforeCursor);
        
        String lastQualifier = null;
        String lastTypeDeclaration = null;
        String lastVarName = null;
        
        while (typedVarMatcher.find()) {
            lastQualifier = typedVarMatcher.group(1); // May be null if not specified
            lastTypeDeclaration = typedVarMatcher.group(2);
            lastVarName = typedVarMatcher.group(3);
        }
        
        if (lastTypeDeclaration != null && isKnownType(lastTypeDeclaration)) {
            String fullType = (lastQualifier != null ? lastQualifier + " " : "") + lastTypeDeclaration;
            LOG.warn("🎯 [inferExpectedType] Found variable declaration with type: " + fullType + " for variable: " + lastVarName);
            LOG.warn("🚨 [DEBUG] Type Inference Result - Variable: '" + lastVarName + "', Type: '" + fullType + "'");
            return fullType;
        }
        
        // If no explicit type declaration is found, proceed with analyzing the context
        // Trim to get the text right before the equals sign
        String trimmedText = textBeforeCursor.trim();
        if (trimmedText.endsWith("=")) {
            trimmedText = trimmedText.substring(0, trimmedText.length() - 1).trim();
        } else if (trimmedText.endsWith(":=")) {
            trimmedText = trimmedText.substring(0, trimmedText.length() - 2).trim();
        } else {
            // This handles cases where there's text after the equals sign
            int equalsIndex = trimmedText.lastIndexOf("=");
            if (equalsIndex > 0) {
                trimmedText = trimmedText.substring(0, equalsIndex).trim();
            }
        }
        
        // Log the text we're analyzing
        LOG.warn("🔍 [inferExpectedType] Analyzing for type inference: '" + trimmedText + "'");
        
        // Extract the variable name and any qualifiers from the left-hand side
        String[] parts = trimmedText.split("\\s+");
        if (parts.length == 0) {
            LOG.warn("🔍 [inferExpectedType] No parts found in text: '" + trimmedText + "'");
            LOG.warn("🚨 [DEBUG] Type Inference Failed - No parts found in text before equals sign");
            return null;
        }
        
        // The variable name should be the last part
        String variableName = parts[parts.length - 1].trim();
        LOG.warn("🔍 [inferExpectedType] Extracted variable name: '" + variableName + "'");
        
        // Check for qualifiers and type in the declaration
        String qualifier = null;
        String type = null;
        
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            
            // Check if this part is a qualifier
            if (part.equals("var") || part.equals("varip") || part.equals("series") || 
                part.equals("simple") || part.equals("const")) {
                qualifier = part;
                LOG.warn("🔍 [inferExpectedType] Found qualifier: " + qualifier);
            } 
            // Check if this part is a known type
            else if (isKnownType(part)) {
                type = part;
                LOG.warn("🎯 [inferExpectedType] Found type declaration: " + type);
            }
        }
        
        // If we found both qualifier and type, combine them
        if (qualifier != null && type != null) {
            String fullType = qualifier + " " + type;
            LOG.warn("🎯 [inferExpectedType] Inferred full type: " + fullType + " for variable: " + variableName);
            LOG.warn("🚨 [DEBUG] Type Inference Result - Variable: '" + variableName + "', Type: '" + fullType + "'");
            return fullType;
        }
        
        // If we only found a type (no qualifier), assume it's a series type
        if (type != null) {
            String fullType = "series " + type;
            LOG.warn("🎯 [inferExpectedType] Inferred type with default series qualifier: " + fullType);
            LOG.warn("🚨 [DEBUG] Type Inference Result - Variable: '" + variableName + "', Type: '" + fullType + "'");
            return fullType;
        }
        
        // Try to find previous declarations of this variable
        String typeFromPrevious = findTypeFromPreviousDeclarations(textBeforeCursor, variableName);
        if (typeFromPrevious != null) {
            LOG.warn("🎯 [inferExpectedType] Found type from previous declarations: " + typeFromPrevious + " for variable: " + variableName);
            LOG.warn("🚨 [DEBUG] Type Inference Result - Variable: '" + variableName + "', Type from previous declaration: '" + typeFromPrevious + "'");
            return typeFromPrevious;
        }
        
        // If we couldn't determine the type, return null (will include all completions)
        LOG.warn("🔍 [inferExpectedType] Could not determine expected type for: '" + trimmedText + "'");
        LOG.warn("🚨 [DEBUG] Type Inference Failed - Could not determine type for variable: '" + variableName + "'");
        return null;
    }

    /**
     * Searches for previous declarations of a variable to determine its type
     * @param text The document text to search
     * @param variableName The variable name to look for
     * @return The detected type, or null if not found
     */
    private String findTypeFromPreviousDeclarations(String text, String variableName) {
        LOG.warn("🚨 [DEBUG] Searching for previous declarations - Variable: '" + variableName + "'");
        
        // Regular expression to find variable declarations with qualifiers
        // This handles cases like:
        // - "var bool varName = " 
        // - "series float varName = "
        // - "varName = "
        Pattern pattern = Pattern.compile(
            "(?:(var|varip|series|simple|const)\\s+)?([a-zA-Z_]\\w*)\\s+(" + 
            Pattern.quote(variableName) + ")\\s*(?:=|:=)"
        );
        Matcher matcher = pattern.matcher(text);
        
        // Find all occurrences (we want the last/most recent one)
        String qualifier = null;
        String type = null;
        int matchCount = 0;
        
        while (matcher.find()) {
            matchCount++;
            String foundQualifier = matcher.group(1); // May be null
            String potentialType = matcher.group(2);
            
            if (isKnownType(potentialType)) {
                qualifier = foundQualifier;
                type = potentialType;
                LOG.info("🔍 [findTypeFromPreviousDeclarations] Found type " + type + 
                        (qualifier != null ? " with qualifier " + qualifier : "") + 
                        " for variable " + variableName);
                LOG.warn("🚨 [DEBUG] Found Previous Declaration - Variable: '" + variableName + 
                        "', Type: '" + potentialType + 
                        (qualifier != null ? "', Qualifier: '" + qualifier : "") + 
                        "', Match #" + matchCount + 
                        ", Text: '" + text.substring(Math.max(0, matcher.start() - 10), 
                                                     Math.min(text.length(), matcher.end() + 10)) + "'");
            } else {
                LOG.warn("🚨 [DEBUG] Found Previous Declaration but type not known - Variable: '" + variableName + 
                        "', Potential Type: '" + potentialType + "', Match #" + matchCount);
            }
        }
        
        if (type != null) {
            // Combine qualifier and type if both are present
            String fullType = (qualifier != null ? qualifier + " " : "series ") + type;
            LOG.warn("🚨 [DEBUG] Final Type from Previous Declarations - Variable: '" + variableName + 
                    "', Full Type: '" + fullType + "'");
            return fullType;
        } else {
            LOG.warn("🚨 [DEBUG] No Valid Type Found in Previous Declarations - Variable: '" + variableName + "'");
            return null;
        }
    }

    /**
     * Checks if a string is a known Pine Script type
     * @param type The type string to check
     * @return True if it's a known type, false otherwise
     */
    private boolean isKnownType(String type) {
        return type != null && (
            type.equals("bool") || type.equals("int") || 
            type.equals("float") || type.equals("string") || 
            type.equals("color") || type.equals("label") ||
            type.equals("line") || type.equals("box") ||
            type.equals("array") || type.equals("matrix") ||
            type.equals("map") || type.equals("table")
        );
    }

    /**
     * Adds completion items that match the expected type
     * @param result The completion result set to add suggestions to
     * @param expectedType The expected type, or null for all types
     * @param version The Pine Script version
     */
    private void addCompletionsForExpectedType(CompletionResultSet result, String expectedType, String version) {
        LOG.info("💡 [addCompletionsForExpectedType] Adding completions for type: " + expectedType);
        
        // Extract the base type and qualifier from the expected type
        TypeInfo targetTypeInfo = parseTypeString(expectedType);
        
        loadDefinitionsForVersion(version);
        Map<String, Set<String>> typedFunctions = new HashMap<>();
        Map<String, Set<String>> typedVariables = new HashMap<>();
        Map<String, Set<String>> typedConstants = new HashMap<>();
        
        // Initialize type maps if they don't exist
        initializeTypeMaps(typedFunctions, typedVariables, typedConstants, version);
        
        // Add all items if no expected type or include type-specific and untyped items
        if (expectedType == null) {
            // Add all functions, variables and constants
            LOG.info("💡 [addCompletionsForExpectedType] No specific type detected, adding all completions");
            addAllCompletionItems(result, version);
        } else {
            LOG.info("💡 [addCompletionsForExpectedType] Adding completions for type: " + expectedType);
            
            // Add type-specific items with type compatibility check
            addTypeSpecificCompletionsWithCompatibility(result, targetTypeInfo, typedFunctions, typedVariables, typedConstants, version);
            
            // Add items with undefined or ambiguous types if compatible
            addUntypedCompletionsWithCompatibility(result, targetTypeInfo, typedFunctions, typedVariables, typedConstants);
        }
    }

    /**
     * Represents Pine Script type information including the base type and qualifier
     */
    private static class TypeInfo {
        String baseType;      // e.g., int, float, bool, string
        String qualifier;     // e.g., series, simple, const, var, varip, local (or null if not specified)
        
        TypeInfo(String baseType, String qualifier) {
            this.baseType = baseType;
            this.qualifier = qualifier;
        }
        
        @Override
        public String toString() {
            return qualifier != null ? qualifier + " " + baseType : baseType;
        }
    }
    
    /**
     * Parse a type string into its base type and qualifier components
     */
    private TypeInfo parseTypeString(String typeString) {
        if (typeString == null) {
            return new TypeInfo("any", null);
        }
        
        String trimmedType = typeString.trim();
        String baseType;
        String qualifier = null;
        
        // Check for common qualifiers
        if (trimmedType.startsWith("series ")) {
            qualifier = "series";
            baseType = trimmedType.substring(7).trim();
        } else if (trimmedType.startsWith("simple ")) {
            qualifier = "simple";
            baseType = trimmedType.substring(7).trim();
        } else if (trimmedType.startsWith("const ")) {
            qualifier = "const";
            baseType = trimmedType.substring(6).trim();
        } else if (trimmedType.startsWith("var ")) {
            qualifier = "var";
            baseType = trimmedType.substring(4).trim();
        } else if (trimmedType.startsWith("varip ")) {
            qualifier = "varip";
            baseType = trimmedType.substring(6).trim();
        } else {
            // Assume it's a local type if no qualifier is specified
            qualifier = "local";
            baseType = trimmedType;
        }
        
        LOG.info("🔍 [parseTypeString] Parsed type '" + typeString + "' into base: '" + baseType + "', qualifier: '" + qualifier + "'");
        return new TypeInfo(baseType, qualifier);
    }
    
    /**
     * Check if an assignment from source type to target type is valid according to Pine Script rules
     * 
     * Assignment compatibility rules:
     * TO series     FROM: series, var local, varip local, local
     * TO simple     FROM: series, simple
     * TO const      FROM: series, simple, const
     * TO var local  FROM: all types
     * TO varip local FROM: all types
     * TO local      FROM: all types
     */
    private boolean isValidAssignment(TypeInfo targetType, TypeInfo sourceType) {
        if (targetType == null || sourceType == null || targetType.baseType == null || sourceType.baseType == null) {
            return true; // Allow if we don't have enough information
        }
        
        // If base types don't match and neither is "any", assignment is invalid
        if (!targetType.baseType.equals("any") && !sourceType.baseType.equals("any") 
            && !targetType.baseType.equals(sourceType.baseType)) {
            LOG.info("❌ [isValidAssignment] Invalid - base types don't match: " + targetType.baseType + " vs " + sourceType.baseType);
            return false;
        }
        
        String targetQualifier = targetType.qualifier != null ? targetType.qualifier : "local";
        String sourceQualifier = sourceType.qualifier != null ? sourceType.qualifier : "local";
        
        // Now check qualifier compatibility
        switch (targetQualifier) {
            case "series":
                // Series can accept: series, var local, varip local, local
                return "series".equals(sourceQualifier) || 
                       "var".equals(sourceQualifier) || 
                       "varip".equals(sourceQualifier) || 
                       "local".equals(sourceQualifier);
                
            case "simple":
                // Simple can accept: series, simple
                return "series".equals(sourceQualifier) || 
                       "simple".equals(sourceQualifier);
                
            case "const":
                // Const can accept: series, simple, const
                return "series".equals(sourceQualifier) || 
                       "simple".equals(sourceQualifier) || 
                       "const".equals(sourceQualifier);
                
            case "var":
            case "varip":
            case "local":
                // These can accept any type qualifier
                return true;
                
            default:
                // Unknown qualifier, assume compatible
                return true;
        }
    }
    
    /**
     * Add completions for a specific expected type considering type compatibility
     */
    private void addTypeSpecificCompletionsWithCompatibility(
            CompletionResultSet result, 
            TypeInfo targetTypeInfo,
            Map<String, Set<String>> typedFunctions,
            Map<String, Set<String>> typedVariables,
            Map<String, Set<String>> typedConstants,
            String version) {
        
        LOG.info("💡 [addTypeSpecificCompletionsWithCompatibility] Adding completions for type: " + targetTypeInfo);
        
        // Filter function return types based on compatibility
        Map<String, String> functionReturnTypes = FUNCTION_RETURN_TYPES.getOrDefault(version, new HashMap<>());
        
        // Add functions that return the expected type
        if (typedFunctions.containsKey(targetTypeInfo.baseType) || "any".equals(targetTypeInfo.baseType)) {
            Set<String> functions = new HashSet<>();
            if ("any".equals(targetTypeInfo.baseType)) {
                // If target type is "any", include all functions
                for (Set<String> funcs : typedFunctions.values()) {
                    functions.addAll(funcs);
                }
            } else {
                functions = typedFunctions.get(targetTypeInfo.baseType);
            }
            
            for (String function : functions) {
                String returnTypeStr = functionReturnTypes.getOrDefault(function, "series " + targetTypeInfo.baseType);
                TypeInfo sourceTypeInfo = parseTypeString(returnTypeStr);
                
                if (isValidAssignment(targetTypeInfo, sourceTypeInfo)) {
                    result.addElement(LookupElementBuilder.create(function)
                        .withPresentableText(function)
                        .withTypeText(returnTypeStr)
                        .withIcon(AllIcons.Nodes.Function)
                        .withBoldness(true));
                    LOG.info("➕ [addTypeSpecificCompletionsWithCompatibility] Added compatible function: " + 
                             function + " (returns: " + returnTypeStr + ")");
                } else {
                    LOG.info("❌ [addTypeSpecificCompletionsWithCompatibility] Skipped incompatible function: " + 
                             function + " (returns: " + returnTypeStr + ", target: " + targetTypeInfo + ")");
                }
            }
        }
        
        // Add variables with compatible types
        Map<String, String> variableTypes = VARIABLE_TYPES.getOrDefault(version, new HashMap<>());
        for (String variable : VARIABLES_MAP.getOrDefault(version, new HashSet<>())) {
            String variableTypeStr = variableTypes.getOrDefault(variable, "series any");
            TypeInfo sourceTypeInfo = parseTypeString(variableTypeStr);
            
            if (isValidAssignment(targetTypeInfo, sourceTypeInfo)) {
                result.addElement(LookupElementBuilder.create(variable)
                    .withPresentableText(variable)
                    .withTypeText(variableTypeStr)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withBoldness(true));
                LOG.info("➕ [addTypeSpecificCompletionsWithCompatibility] Added compatible variable: " + 
                         variable + " (type: " + variableTypeStr + ")");
            }
        }
        
        // Add constants with compatible types
        Map<String, String> constantTypes = CONSTANT_TYPES.getOrDefault(version, new HashMap<>());
        for (String constant : CONSTANTS_MAP.getOrDefault(version, new HashSet<>())) {
            String constantTypeStr = constantTypes.getOrDefault(constant, "const any");
            TypeInfo sourceTypeInfo = parseTypeString(constantTypeStr);
            
            if (isValidAssignment(targetTypeInfo, sourceTypeInfo)) {
                result.addElement(LookupElementBuilder.create(constant)
                    .withPresentableText(constant)
                    .withTypeText(constantTypeStr)
                    .withIcon(AllIcons.Nodes.Constant)
                    .withBoldness(true));
                LOG.info("➕ [addTypeSpecificCompletionsWithCompatibility] Added compatible constant: " + 
                         constant + " (type: " + constantTypeStr + ")");
            }
        }
    }

    /**
     * Add completions for undefined or ambiguous types with compatibility check
     */
    private void addUntypedCompletionsWithCompatibility(
            CompletionResultSet result,
            TypeInfo targetTypeInfo,
            Map<String, Set<String>> typedFunctions,
            Map<String, Set<String>> typedVariables,
            Map<String, Set<String>> typedConstants) {
        
        LOG.info("💡 [addUntypedCompletionsWithCompatibility] Adding completions with unknown types");
        
        // Get all typed items to exclude them
        Set<String> allTypedFunctions = new HashSet<>();
        Set<String> allTypedVariables = new HashSet<>();
        Set<String> allTypedConstants = new HashSet<>();
        
        for (Set<String> functions : typedFunctions.values()) {
            allTypedFunctions.addAll(functions);
        }
        
        for (Set<String> variables : typedVariables.values()) {
            allTypedVariables.addAll(variables);
        }
        
        for (Set<String> constants : typedConstants.values()) {
            allTypedConstants.addAll(constants);
        }
        
        // For untyped items, we assume they could be compatible
        // But we can still check the target qualifier for restrictions
        
        // Add untyped functions (assume series return type)
        TypeInfo defaultFunctionType = new TypeInfo("any", "series");
        for (String function : FUNCTIONS_MAP.getOrDefault(DEFAULT_VERSION, new HashSet<>())) {
            if (!allTypedFunctions.contains(function) && isValidAssignment(targetTypeInfo, defaultFunctionType)) {
                result.addElement(LookupElementBuilder.create(function)
                    .withPresentableText(function)
                    .withTypeText("series any")
                    .withIcon(AllIcons.Nodes.Function));
            }
        }
        
        // Add untyped variables (assume series type)
        TypeInfo defaultVarType = new TypeInfo("any", "series");
        for (String variable : VARIABLES_MAP.getOrDefault(DEFAULT_VERSION, new HashSet<>())) {
            if (!allTypedVariables.contains(variable) && isValidAssignment(targetTypeInfo, defaultVarType)) {
                result.addElement(LookupElementBuilder.create(variable)
                    .withPresentableText(variable)
                    .withTypeText("series any")
                    .withIcon(AllIcons.Nodes.Variable));
            }
        }
        
        // Add untyped constants (assume const type)
        TypeInfo defaultConstType = new TypeInfo("any", "const");
        for (String constant : CONSTANTS_MAP.getOrDefault(DEFAULT_VERSION, new HashSet<>())) {
            if (!allTypedConstants.contains(constant) && isValidAssignment(targetTypeInfo, defaultConstType)) {
                result.addElement(LookupElementBuilder.create(constant)
                    .withPresentableText(constant)
                    .withTypeText("const any")
                    .withIcon(AllIcons.Nodes.Constant));
            }
        }
    }

    /**
     * Initialize the type maps for functions, variables, and constants
     */
    private void initializeTypeMaps(Map<String, Set<String>> typedFunctions, 
                                   Map<String, Set<String>> typedVariables,
                                   Map<String, Set<String>> typedConstants,
                                   String version) {
        LOG.warn("🚨 [initializeTypeMaps] Initializing type maps for version: " + version);

        // Instead of hardcoding functions, get them from the JSON files
        Map<String, String> functionReturnTypes = FUNCTION_RETURN_TYPES.getOrDefault(version, new HashMap<>());
        Map<String, String> variableTypes = VARIABLE_TYPES.getOrDefault(version, new HashMap<>());
        Map<String, String> constantTypes = CONSTANT_TYPES.getOrDefault(version, new HashMap<>());
        
        // Process functions by return type
        for (Map.Entry<String, String> entry : functionReturnTypes.entrySet()) {
            String functionName = entry.getKey();
            String returnType = entry.getValue();
            
            // Extract the base type (without qualifiers)
            String baseType = returnType.replaceAll("^(?:series|simple|const|input|var|varip)\\s+", "");
            baseType = baseType.replaceAll("\\s*\\[.*\\]\\s*$", ""); // Remove array notation if present
            
            // Add to the appropriate type category
            typedFunctions.computeIfAbsent(baseType, k -> new HashSet<>()).add(functionName);
            LOG.info("🔍 [initializeTypeMaps] Added function '" + functionName + "' to type '" + baseType + "' from return type '" + returnType + "'");
        }
        
        // Process variables by type
        for (Map.Entry<String, String> entry : variableTypes.entrySet()) {
            String variableName = entry.getKey();
            String variableType = entry.getValue();
            
            // Extract the base type (without qualifiers)
            String baseType = variableType.replaceAll("^(?:series|simple|const|input|var|varip)\\s+", "");
            baseType = baseType.replaceAll("\\s*\\[.*\\]\\s*$", ""); // Remove array notation if present
            
            // Add to the appropriate type category
            typedVariables.computeIfAbsent(baseType, k -> new HashSet<>()).add(variableName);
            LOG.info("🔍 [initializeTypeMaps] Added variable '" + variableName + "' to type '" + baseType + "' from variable type '" + variableType + "'");
        }
        
        // Process constants by type
        for (Map.Entry<String, String> entry : constantTypes.entrySet()) {
            String constantName = entry.getKey();
            String constantType = entry.getValue();
            
            // Extract the base type (without qualifiers)
            String baseType = constantType.replaceAll("^(?:series|simple|const|input|var|varip)\\s+", "");
            baseType = baseType.replaceAll("\\s*\\[.*\\]\\s*$", ""); // Remove array notation if present
            
            // Add to the appropriate type category
            typedConstants.computeIfAbsent(baseType, k -> new HashSet<>()).add(constantName);
            LOG.info("🔍 [initializeTypeMaps] Added constant '" + constantName + "' to type '" + baseType + "' from constant type '" + constantType + "'");
        }
        
        // Handle boolean constants specifically
        if (!typedConstants.containsKey("bool") || !typedConstants.get("bool").contains("true")) {
            typedConstants.computeIfAbsent("bool", k -> new HashSet<>()).add("true");
            typedConstants.computeIfAbsent("bool", k -> new HashSet<>()).add("false");
            LOG.info("🔍 [initializeTypeMaps] Added default boolean constants 'true' and 'false'");
        }
        
        // Add NA as a special value that can be assigned to any type
        for (String baseType : new String[]{"int", "float", "bool", "string", "color", "any"}) {
            typedConstants.computeIfAbsent(baseType, k -> new HashSet<>()).add("na");
            LOG.info("🔍 [initializeTypeMaps] Added 'na' as assignable to type '" + baseType + "'");
        }
        
        LOG.warn("🚨 [initializeTypeMaps] Completed type maps initialization for version: " + version);
    }

    /**
     * Add all completion items regardless of type
     */
    private void addAllCompletionItems(CompletionResultSet result, String version) {
        // Add functions with return types
        Map<String, String> functionReturnTypes = FUNCTION_RETURN_TYPES.getOrDefault(version, new HashMap<>());
        LOG.info("🔍 [addAllCompletionItems] Adding functions with return types. Found " + functionReturnTypes.size() + " return types");
        
        for (String function : FUNCTIONS_MAP.getOrDefault(version, new HashSet<>())) {
            String returnType = functionReturnTypes.getOrDefault(function, "series any");
            LOG.info("➕ [addAllCompletionItems] Adding function: " + function + " with return type: " + returnType);
            result.addElement(LookupElementBuilder.create(function)
                .withPresentableText(function)
                .withTypeText(returnType)
                .withIcon(AllIcons.Nodes.Function));
        }
        
        // Add variables with types
        Map<String, String> variableTypes = VARIABLE_TYPES.getOrDefault(version, new HashMap<>());
        LOG.info("🔍 [addAllCompletionItems] Adding variables with types. Found " + variableTypes.size() + " variable types");
        
        for (String variable : VARIABLES_MAP.getOrDefault(version, new HashSet<>())) {
            String type = variableTypes.getOrDefault(variable, "series any");
            LOG.info("➕ [addAllCompletionItems] Adding variable: " + variable + " with type: " + type);
            result.addElement(LookupElementBuilder.create(variable)
                .withPresentableText(variable)
                .withTypeText(type)
                .withIcon(AllIcons.Nodes.Variable));
        }
        
        // Add constants with types
        Map<String, String> constantTypes = CONSTANT_TYPES.getOrDefault(version, new HashMap<>());
        LOG.info("🔍 [addAllCompletionItems] Adding constants with types. Found " + constantTypes.size() + " constant types");
        
        for (String constant : CONSTANTS_MAP.getOrDefault(version, new HashSet<>())) {
            String type = constantTypes.getOrDefault(constant, "const any");
            LOG.info("➕ [addAllCompletionItems] Adding constant: " + constant + " with type: " + type);
            result.addElement(LookupElementBuilder.create(constant)
                .withPresentableText(constant)
                .withTypeText(type)
                .withIcon(AllIcons.Nodes.Constant));
        }
    }
    
    /**
     * Adds namespace method completions to the result.
     */
    private void addNamespaceMethodCompletions(CompletionResultSet result, String namespace, String version) {
        LOG.warn("🔍 [addNamespaceMethodCompletions] CALLED for namespace: '" + namespace + "', version: " + version);
        LOG.warn("🔍 [addNamespaceMethodCompletions] Using prefix matcher: '" + result.getPrefixMatcher().getPrefix() + "'");
        
        try {
            // Try to get field access to count elements added
            java.lang.reflect.Field field = result.getClass().getDeclaredField("myItems");
            field.setAccessible(true);
            Object itemsObj = field.get(result);
            if (itemsObj instanceof Collection) {
                int initialResultSize = ((Collection<?>) itemsObj).size();
                LOG.warn("🔍 [addNamespaceMethodCompletions] Initial result size: " + initialResultSize);
            }
        } catch (Exception e) {
            LOG.warn("🔍 [addNamespaceMethodCompletions] Unable to get result size: " + e.getMessage());
        }
        
        // Get the sets for this version
        Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
        Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
        Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
        
        // Get type maps for returning type information
        Map<String, String> functionReturnTypes = FUNCTION_RETURN_TYPES.getOrDefault(version, new HashMap<>());
        Map<String, String> variableTypes = VARIABLE_TYPES.getOrDefault(version, new HashMap<>());
        Map<String, String> constantTypes = CONSTANT_TYPES.getOrDefault(version, new HashMap<>());
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Available sets - functions: " + functions.size() + 
                 ", variables: " + variables.size() + ", constants: " + constants.size());
        
        // Create a dedicated result set with an empty prefix matcher to ensure everything is shown
        CompletionResultSet exactResult = result.withPrefixMatcher("");
        LOG.warn("🔍 [addNamespaceMethodCompletions] Created new result set with empty prefix matcher");
        
        // Keep track of all members added to avoid duplicates
        Set<String> addedMembers = new HashSet<>();
        int totalAddedCount = 0;
        
        // First check if it's a built-in namespace
        if (Arrays.asList(NAMESPACES).contains(namespace)) {
            Map<String, String[]> namespaceMethods = NAMESPACE_METHODS_CACHE.getOrDefault(version, 
                                                 initNamespaceMethodsForVersion(version, CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>())));
            
            if (namespaceMethods.containsKey(namespace)) {
                String[] methods = namespaceMethods.get(namespace);
                LOG.warn("🔍 [addNamespaceMethodCompletions] Found " + methods.length + " methods for built-in namespace: " + namespace);
                
                if (methods.length > 0) {
                    LOG.warn("🔍 [addNamespaceMethodCompletions] First 10 methods: " + 
                             String.join(", ", Arrays.copyOfRange(methods, 0, Math.min(10, methods.length))));
                }
                
                int addedCount = 0;
                for (String method : methods) {
                    // Skip if already added
                    if (addedMembers.contains(method)) {
                        continue;
                    }
                    
                    addedMembers.add(method);
                    
                    // Get return type for the method
                    String fullName = namespace + "." + method;
                    String returnType = functionReturnTypes.getOrDefault(fullName, "any");
                    
                    LookupElementBuilder element = LookupElementBuilder.create(method)
                            .withIcon(AllIcons.Nodes.Method)
                            .withTypeText(returnType)
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
                    
                    // Add with higher priority to the exact result set
                    exactResult.addElement(PrioritizedLookupElement.withPriority(element, 900));
                    addedCount++;
                }
                LOG.warn("🔍 [addNamespaceMethodCompletions] Added " + addedCount + " built-in namespace method completions");
                totalAddedCount += addedCount;
            } else {
                LOG.warn("🔍 [addNamespaceMethodCompletions] No methods found for built-in namespace: " + namespace);
            }
        } else {
            LOG.warn("🔍 [addNamespaceMethodCompletions] Not a built-in namespace: " + namespace);
        }
        
        // Collect all namespace members from all categories (functions, variables, constants)
        // but treat them consistently based on their behavior
        
        // Function-like members (shown with () and parameter info)
        Set<String> functionMembers = new HashSet<>();
        for (String func : functions) {
            if (func.contains(".") && func.startsWith(namespace + ".")) {
                String member = func.substring(namespace.length() + 1);
                // Handle possible multi-level namespaces
                if (member.contains(".")) {
                    // Extract only the next level
                    member = member.substring(0, member.indexOf('.'));
                }
                functionMembers.add(member);
            }
        }
        
        // Variable-like members (shown as properties)
        Set<String> propertyMembers = new HashSet<>();
        // Collect from variables
        for (String var : variables) {
            if (var.contains(".") && var.startsWith(namespace + ".")) {
                String member = var.substring(namespace.length() + 1);
                // Handle possible multi-level namespaces
                if (member.contains(".")) {
                    // Extract only the next level
                    member = member.substring(0, member.indexOf('.'));
                }
                propertyMembers.add(member);
            }
        }
        // Collect from constants
        for (String cons : constants) {
            if (cons.contains(".") && cons.startsWith(namespace + ".")) {
                String member = cons.substring(namespace.length() + 1);
                // Handle possible multi-level namespaces
                if (member.contains(".")) {
                    // Extract only the next level
                    member = member.substring(0, member.indexOf('.'));
                }
                propertyMembers.add(member);
            }
        }
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Found: " +
                functionMembers.size() + " function members, " +
                propertyMembers.size() + " property members");
        
        // Add function members
        for (String member : functionMembers) {
            // Skip if already added from built-in methods
            if (addedMembers.contains(member)) {
                continue;
            }
            
            addedMembers.add(member);
            
            LookupElementBuilder element = LookupElementBuilder.create(member)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText(namespace + " method")
                    .withInsertHandler((ctx, item) -> {
                        // Check if this is a function or a sub-namespace
                        boolean isFunction = false;
                        for (String func : functions) {
                            if (func.equals(namespace + "." + member)) {
                                isFunction = true;
                                break;
                            }
                        }
                        
                        Editor editor = ctx.getEditor();
                        if (isFunction) {
                            // Add parentheses for functions and position caret inside them
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Trigger parameter info popup
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).autoPopupParameterInfo(editor, null);
                            });
                        } else {
                            // It's a sub-namespace, add a dot and trigger member lookup
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                            });
                        }
                    });
            exactResult.addElement(PrioritizedLookupElement.withPriority(element, 850));
            totalAddedCount++;
        }
        
        // Add property members
        for (String member : propertyMembers) {
            // Skip if already added as a function member
            if (addedMembers.contains(member)) {
                continue;
            }
            
            addedMembers.add(member);
            
            LookupElementBuilder element = LookupElementBuilder.create(member)
                    .withIcon(AllIcons.Nodes.Property)
                    .withTypeText(namespace + " property")
                    .withInsertHandler((ctx, item) -> {
                        // Check if this might be a sub-namespace
                        boolean isProperty = false;
                        for (String var : variables) {
                            if (var.equals(namespace + "." + member)) {
                                isProperty = true;
                                break;
                            }
                        }
                        for (String cons : constants) {
                            if (cons.equals(namespace + "." + member)) {
                                isProperty = true;
                                break;
                            }
                        }
                        
                        Editor editor = ctx.getEditor();
                        if (!isProperty) {
                            // It's possibly a sub-namespace, add a dot and trigger member lookup
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                            });
                        }
                    });
            exactResult.addElement(PrioritizedLookupElement.withPriority(element, 750));
            totalAddedCount++;
        }
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Total members added: " + totalAddedCount);
        
        // If no suggestions were added, try to provide some guidance
        if (totalAddedCount == 0) {
            List<String> definitions = CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>());
            LOG.warn("🔍 [addNamespaceMethodCompletions] No members found for namespace: " + namespace + ". This may be a custom namespace.");
            
            // Look for any definitions that might contain this namespace to provide hints
            if (definitions.size() > 0) {
                LOG.warn("🔍 [addNamespaceMethodCompletions] Checking " + definitions.size() + " total definitions for any that might contain '" + namespace + "'");
                for (String def : definitions) {
                    if (def.startsWith(namespace) || def.contains("." + namespace + ".")) {
                        LOG.warn("🔍 [addNamespaceMethodCompletions] Related definition: " + def);
                    }
                }
            }
        }
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Completion done for namespace: " + namespace);
    }
    
    /**
     * Adds function parameter completions to the result.
     */
    private void addFunctionParameterCompletions(CompletionResultSet result, String functionName, String version) {
        if (functionName == null) {
            LOG.warn(">>>>>> Cannot add parameter completions for null function name");
            return;
        }
        
        LOG.warn(">>>>>> Adding parameter completions for function: " + functionName + ", version: " + version);
        
        // Check if we have parameter information for this function
        Map<String, List<Map<String, String>>> functionArgs = FUNCTION_ARGUMENTS_CACHE.getOrDefault(version, new HashMap<>());
        
        if (functionArgs.containsKey(functionName)) {
            List<Map<String, String>> args = functionArgs.get(functionName);
            LOG.warn(">>>>>> Found " + args.size() + " parameters for function: " + functionName);
            
            // Log the parameter names and types
            for (int i = 0; i < args.size(); i++) {
                Map<String, String> param = args.get(i);
                String paramName = param.getOrDefault("name", "unnamed");
                String paramType = param.getOrDefault("type", "unknown");
                LOG.warn(">>>>>> Parameter " + i + ": " + paramName + " (" + paramType + ")");
            }
            
            // If we know which parameter index the user is typing at, add specific completions for that parameter
            if (currentParamIndex >= 0) {
                LOG.warn(">>>>>> Adding specific completions for parameter index: " + currentParamIndex);
                addParameterCompletions(result, functionName, currentParamIndex, version);
            }
        } else {
            LOG.warn(">>>>>> No parameter information found for function: " + functionName);
            
            // Try to get parameter info from function parameters cache
            Map<String, Map<String, String>> functionParameters = FUNCTION_PARAMETERS_CACHE.getOrDefault(version, new HashMap<>());
            Map<String, String> parameterMap = functionParameters.get(functionName);
            
            if (parameterMap != null && !parameterMap.isEmpty()) {
                LOG.warn(">>>>>> Found parameter information in FUNCTION_PARAMETERS_CACHE: " + parameterMap.size() + " parameters");
                
                // Log the parameter names and types
                for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                    LOG.warn(">>>>>> Parameter: " + entry.getKey() + " (" + entry.getValue() + ")");
                }
                
                if (currentParamIndex >= 0) {
                    LOG.warn(">>>>>> Adding specific completions for parameter index: " + currentParamIndex);
                    addParameterCompletions(result, functionName, currentParamIndex, version);
                }
            } else {
                LOG.warn(">>>>>> No parameter information found in FUNCTION_PARAMETERS_CACHE either");
                
                // Check if this function has namespace
                if (functionName.contains(".")) {
                    String[] parts = functionName.split("\\.", 2);
                    String namespace = parts[0];
                    String methodName = parts[1];
                    
                    LOG.warn(">>>>>> Function is namespaced: " + namespace + "." + methodName);
                    
                    // Get namespace methods from cache
                    Map<String, String[]> namespaceMethods = NAMESPACE_METHODS_CACHE.getOrDefault(version, new HashMap<>());
                    String[] methods = namespaceMethods.get(namespace);
                    
                    if (methods != null) {
                        LOG.warn(">>>>>> Found " + methods.length + " methods for namespace: " + namespace);
                        boolean methodFound = false;
                        
                        for (String method : methods) {
                            if (method.equals(methodName)) {
                                methodFound = true;
                                LOG.warn(">>>>>> Method found in namespace methods list");
                                break;
                            }
                        }
                        
                        if (!methodFound) {
                            LOG.warn(">>>>>> Method not found in namespace methods list");
                        }
                    } else {
                        LOG.warn(">>>>>> No methods found for namespace: " + namespace);
                    }
                }
                
                // Try to add generic parameter completions based on function name
                if (currentParamIndex >= 0) {
                    Map<String, String> suggestions = getParameterSuggestions(functionName, currentParamIndex);
                    if (!suggestions.isEmpty()) {
                        LOG.warn(">>>>>> Adding generic suggestions based on function name and index: " + suggestions.size() + " suggestions");
                    } else {
                        LOG.warn(">>>>>> No generic suggestions available for this function and index");
                    }
                }
            }
        }
    }
    
    /**
     * Analyzes text to determine function call context
     * This sets the isInsideFunctionCall, currentFunctionName, and currentParamIndex fields
     */
    private void checkFunctionCallContext(String documentText, int offset) {
        try {
            // Check if we're inside a function call
            if (isInsideFunctionCall(documentText, offset)) {
                isInsideFunctionCall = true;
                
                // Extract the current function name and parameter index
                currentFunctionName = extractFunctionName(documentText.substring(0, offset));
                currentParamIndex = getParamIndexFromText(documentText.substring(0, offset));
                
                LOG.info("🔎 [checkFunctionCallContext] In function: " + currentFunctionName + ", param index: " + currentParamIndex);
            } else {
                isInsideFunctionCall = false;
                currentFunctionName = null;
                currentParamIndex = 0;
            }
        } catch (Exception e) {
            LOG.error("Error in checkFunctionCallContext", e);
        }
    }
    
    /**
     * Adds parameter-specific completions for the given function and parameter index.
     */
    private void addParameterCompletions(CompletionResultSet result, String functionName, int paramIndex, String version) {
        // Get function arguments from cache for this version
        Map<String, List<Map<String, String>>> functionArgs = FUNCTION_ARGUMENTS_CACHE.getOrDefault(version, new HashMap<>());
        
        LOG.warn(">>>>>> addParameterCompletions for " + functionName + ", param index: " + paramIndex + ", version: " + version);
        
        // If we have argument definitions for this function
        if (functionArgs.containsKey(functionName)) {
            List<Map<String, String>> args = functionArgs.get(functionName);
            LOG.warn(">>>>>> Found " + args.size() + " arguments for function: " + functionName);
            
            // If there are arguments defined and the current parameter index is valid
            if (!args.isEmpty() && paramIndex < args.size()) {
                // Get the current parameter information
                Map<String, String> param = args.get(paramIndex);
                String paramName = param.getOrDefault("name", "");
                String paramType = param.getOrDefault("type", "");
                
                LOG.warn(">>>>>> Current parameter: " + paramName + " of type: " + paramType);
                
                if (!paramName.isEmpty()) {
                    // Add named parameter option with super high priority (3000)
                    LookupElementBuilder namedElement = LookupElementBuilder.create(paramName + "=")
                            .withIcon(AllIcons.Nodes.Parameter)
                            .withTypeText(paramType)
                            .withTailText(" (named)", true)
                            .withInsertHandler((ctx, item) -> {
                                // Move caret after equals sign to let user input the value
                                Editor editor = ctx.getEditor();
                                editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                            });
                    result.addElement(PrioritizedLookupElement.withPriority(namedElement, 3000)); // Highest priority for current parameter
                    LOG.warn(">>>>>> Added named parameter option: " + paramName + "=");
                    
                    // Also suggest possible values based on parameter type
                    Map<String, String> valueSuggestions = getValueSuggestionsForType(paramType, paramName, functionName, paramIndex);
                    LOG.warn(">>>>>> Generated " + valueSuggestions.size() + " value suggestions for type: " + paramType);
                    
                    for (Map.Entry<String, String> entry : valueSuggestions.entrySet()) {
                        LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                                .withTypeText(entry.getValue())
                                .withIcon(AllIcons.Nodes.Parameter);
                        result.addElement(PrioritizedLookupElement.withPriority(element, 2950)); // Very high priority for parameter values
                        LOG.warn(">>>>>> Added value suggestion: " + entry.getKey() + " (" + entry.getValue() + ")");
                    }
                } else {
                    LOG.warn(">>>>>> Parameter name is empty, cannot add named parameter suggestion");
                }
            } else {
                LOG.warn(">>>>>> Parameter index " + paramIndex + " is out of bounds or no arguments defined");
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
                        result.addElement(PrioritizedLookupElement.withPriority(element, 2900)); // High priority for other parameters
                        LOG.warn(">>>>>> Added other parameter option: " + otherParamName + "= (index " + i + ")");
                    }
                }
            }
        } else {
            LOG.warn(">>>>>> No argument definitions found for function: " + functionName);
        }
        
        // Check if we have special value completions for this parameter
        Map<String, String> specialSuggestions = getParameterSuggestions(functionName, paramIndex);
        if (!specialSuggestions.isEmpty()) {
            LOG.warn(">>>>>> Found " + specialSuggestions.size() + " special suggestions for parameter index " + paramIndex);
            for (Map.Entry<String, String> entry : specialSuggestions.entrySet()) {
                LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                        .withTypeText(entry.getValue())
                        .withIcon(AllIcons.Nodes.Parameter);
                result.addElement(PrioritizedLookupElement.withPriority(element, 2920)); // High priority for special parameter suggestions
                LOG.warn(">>>>>> Added special suggestion: " + entry.getKey() + " (" + entry.getValue() + ")");
            }
        } else {
            LOG.warn(">>>>>> No special suggestions found for parameter index " + paramIndex);
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
        // Check if we're inside a function call to adjust keyword priorities
        boolean insideFunctionCall = isInsideFunctionCall(documentText, offset);
        int keywordPriority = insideFunctionCall ? 400 : 1000;
        
        // Add keywords with appropriate priority (lower when inside function calls)
        for (String keyword : KEYWORDS) {
            LookupElementBuilder element = LookupElementBuilder.create(keyword)
                    .withIcon(AllIcons.Nodes.Favorite)
                    .withTypeText("keyword");
            result.addElement(PrioritizedLookupElement.withPriority(element, keywordPriority));
        }
        
        // Get sets for this version
        Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
        Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
        Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
        
        // Add built-in definitions from the cached definitions with namespace handling
        List<String> definitions = CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>());
        
        // Use a single set to track all added namespaces to avoid duplication
        Set<String> addedNamespaces = new HashSet<>();
        
        for (String definition : definitions) {
            // If the definition contains a dot, add a namespace suggestion and skip full name
            if (definition.contains(".")) {
                String ns = definition.substring(0, definition.indexOf('.'));
                
                // Only add namespace once, regardless of type (function, variable, constant)
                if (!addedNamespaces.contains(ns)) {
                    addedNamespaces.add(ns);
                    
                    // Create namespace element with consistent icon and type text
                    LookupElementBuilder nsElement = LookupElementBuilder.create(ns)
                        .withIcon(PineScriptIcons.NAMESPACE)  // Use the dedicated namespace icon
                        .withTypeText("namespace")  // Just show "namespace" not the specific kind
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                    
                    // Use a consistent high priority for all namespaces
                    result.addElement(PrioritizedLookupElement.withPriority(nsElement, 850));
                }
                
                // Skip adding the full namespaced definition
                continue;
            }
            
            // For non-namespaced definitions, use the existing logic
            if (Arrays.asList(NAMESPACES).contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(PineScriptIcons.NAMESPACE)  // Use same icon for consistency
                        .withTypeText("namespace")
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 900));
            } else if (functions.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Function)
                        .withTypeText("function")
                        .withTailText("()", true)
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add parentheses
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            
                            // Position caret inside the parentheses
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Force an immediate popup for parameters
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 850));
            } else if (constants.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Constant)
                        .withTypeText("constant")
                        .withBoldness(true);
                result.addElement(PrioritizedLookupElement.withPriority(element, 800));
            } else if (variables.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTypeText("variable");
                result.addElement(PrioritizedLookupElement.withPriority(element, 750));
            }
        }
        
        // Add type names
        for (String type : TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("type")
                    .withInsertHandler((ctx, item) -> {
                        Editor editor = ctx.getEditor();
                        
                        // Add the dot without triggering typed action
                        EditorModificationUtil.insertStringAtCaret(editor, ".");
                        
                        // Force an immediate popup
                        Project project = ctx.getProject();
                        if (project != null) {
                            try {
                                AutoPopupController controller = AutoPopupController.getInstance(project);
                                controller.autoPopupMemberLookup(editor, null);
                            } catch (Exception e) {
                                LOG.warn("Error showing member lookup: " + e.getMessage());
                            }
                        }
                    });
            result.addElement(PrioritizedLookupElement.withPriority(element, 700));
        }
    }
    
    /**
     * Adds completions from variables and functions scanned in the current document.
     */
    private void addScannedCompletions(@NotNull CompletionParameters parameters, 
                                      @NotNull CompletionResultSet result) {
        addScannedCompletions(parameters, result, null);
    }

    /**
     * Scans the document and adds completions for variables and functions found in the code
     * with an option to filter by expected type
     */
    private void addScannedCompletions(@NotNull CompletionParameters parameters, 
                                      @NotNull CompletionResultSet result,
                                      String expectedType) {
        // Get document and offset
        Document document = parameters.getOriginalFile().getViewProvider().getDocument();
        if (document == null) return;
        
        String documentText = document.getText();
        int offset = parameters.getOffset();
        
        // Use the provided expected type or try to infer it
        String textBeforeCursor = documentText.substring(0, offset);
        if (expectedType == null) {
            // Check if we're after an equals sign
            if (textBeforeCursor.trim().endsWith("=") || 
                (textBeforeCursor.contains("=") && textBeforeCursor.substring(textBeforeCursor.lastIndexOf("=") + 1).trim().isEmpty())) {
                expectedType = inferExpectedType(textBeforeCursor);
                LOG.info("🔍 [addScannedCompletions] Inferred expected type for completion: " + expectedType);
            }
        } else {
            LOG.info("🔍 [addScannedCompletions] Using provided expected type: " + expectedType);
        }
        
        // Parse the expected type into base type and qualifier for compatibility checks
        TypeInfo targetTypeInfo = expectedType != null ? parseTypeString(expectedType) : null;
        
        // Enhanced logging for variable assignment context
        String varNameBeingAssigned = "";
        if (textBeforeCursor.trim().endsWith("=")) {
            // Extract variable name from left side of equals
            String leftSide = textBeforeCursor.trim().substring(0, textBeforeCursor.trim().length() - 1).trim();
            String[] parts = leftSide.split("\\s+");
            if (parts.length > 0) {
                varNameBeingAssigned = parts[parts.length - 1];
            }
        } else if (textBeforeCursor.contains("=")) {
            String leftSide = textBeforeCursor.substring(0, textBeforeCursor.lastIndexOf("=")).trim();
            String[] parts = leftSide.split("\\s+");
            if (parts.length > 0) {
                varNameBeingAssigned = parts[parts.length - 1];
            }
        }
        
        LOG.warn("🚨 [DEBUG] Variable Assignment Context - Name: '" + varNameBeingAssigned + 
                 "', Expected Type: '" + expectedType + "', Text before cursor: '" + 
                 (textBeforeCursor.length() > 30 ? "..." + textBeforeCursor.substring(textBeforeCursor.length() - 30) : textBeforeCursor) + "'");
        
        // Track found variables to avoid duplicates
        Set<String> foundVars = new HashSet<>();
        Map<String, String> varTypeMap = new HashMap<>();
        
        // Log expected type for debugging
        if (expectedType != null) {
            LOG.info("🎯 [addScannedCompletions] Looking for variables with type: " + expectedType);
            LOG.warn("🚨 [DEBUG] Using expected type filter: '" + expectedType + "' for suggestions");
        } else {
            LOG.warn("🚨 [DEBUG] No type filter applied - showing all variable types");
        }
        
        // Check if we're in a request or declaration by examining the context
        boolean inVariableDeclaration = false;
        
        if (textBeforeCursor.trim().matches(".*(?:var|varip)\\s+(?:[a-zA-Z_]\\w*)(?:\\s+[a-zA-Z_]\\w*)?\\s*(?:=)?\\s*$")) {
            LOG.info("🔍 [addScannedCompletions] In variable declaration context");
            LOG.warn("🚨 [DEBUG] Detected variable declaration context - may limit suggestions");
            inVariableDeclaration = true;
        }
        
        // Regex patterns for variable detection
        
        // 1. Match variable declarations with explicit type: var TYPE varName = value or TYPE varName = value
        Pattern typedVarPattern = Pattern.compile("(?:var\\s+|varip\\s+)?([a-zA-Z_]\\w*)\\s+([a-zA-Z_]\\w*)\\s*=");
        Matcher typedVarMatcher = typedVarPattern.matcher(documentText);
        
        while (typedVarMatcher.find()) {
            String typeStr = typedVarMatcher.group(1);
            String varName = typedVarMatcher.group(2);
            
            // Check if it's a valid type (not just another variable)
            if (isKnownType(typeStr)) {
                // Store original type string
                varTypeMap.put(varName, typeStr);
                LOG.info("🔍 [addScannedCompletions] Found typed variable: " + varName + " with type: " + typeStr);
                
                // MODIFIED: Always add the variable unless we're in a variable declaration context
                // Or if we're in a typed context, check compatibility
                boolean shouldAdd = false;
                
                if (targetTypeInfo == null) {
                    // No type constraint, always add unless in variable declaration
                    shouldAdd = !inVariableDeclaration;
                    LOG.warn("🚨 [DEBUG] No type constraint - adding variable: " + varName);
                } else {
                    // We have a type constraint, check compatibility
                    TypeInfo sourceTypeInfo = parseTypeString(typeStr);
                    shouldAdd = isValidAssignment(targetTypeInfo, sourceTypeInfo);
                    LOG.warn("🚨 [DEBUG] Type compatibility check for: " + varName + 
                            " - source: " + sourceTypeInfo + ", target: " + targetTypeInfo + 
                            ", compatible: " + shouldAdd);
                }
                
                if (shouldAdd && !foundVars.contains(varName)) {
                    result.addElement(LookupElementBuilder.create(varName)
                        .withPresentableText(varName)
                        .withTypeText(typeStr)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withBoldness(true));
                    foundVars.add(varName);
                    LOG.info("➕ [addScannedCompletions] Added typed variable to completions: " + varName + " (type: " + typeStr + ")");
                    LOG.warn("🚨 [DEBUG] Suggestion Added - Variable: '" + varName + "', Type: '" + typeStr + "'");
                } else if (!shouldAdd) {
                    LOG.info("⏩ [addScannedCompletions] Skipped variable " + varName + " - type " + typeStr + 
                             " doesn't match expected " + expectedType + " or in variable declaration context");
                    LOG.warn("🚨 [DEBUG] Suggestion Skipped - Variable: '" + varName + 
                             "', Type: '" + typeStr + "', Expected Type: '" + expectedType + 
                             "', In Variable Declaration: " + inVariableDeclaration);
                }
            }
        }
        
        // 2. Match untyped variable declarations: var/varip varName = value
        Pattern untypedVarPattern = Pattern.compile("(?:var|varip)\\s+([a-zA-Z_]\\w*)\\s*=");
        Matcher untypedVarMatcher = untypedVarPattern.matcher(documentText);
        
        while (untypedVarMatcher.find()) {
            String varName = untypedVarMatcher.group(1);
            
            // Skip if we already found this variable with a type
            if (!varTypeMap.containsKey(varName) && !foundVars.contains(varName)) {
                // Try to infer type from assignment
                String inferredType = inferTypeFromAssignment(documentText, varName);
                
                if (inferredType != null) {
                    varTypeMap.put(varName, inferredType);
                    LOG.info("🔍 [addScannedCompletions] Found untyped variable with inferred type: " + varName + " - " + inferredType);
                    LOG.warn("🚨 [DEBUG] Inferred Type - Variable: '" + varName + "', Inferred Type: '" + inferredType + 
                             "', Expected Type: '" + expectedType + "'");
                    
                    // MODIFIED: Always add the variable unless we're in a variable declaration context
                    // Or if we're in a typed context, check compatibility
                    boolean shouldAdd = false;
                    
                    if (targetTypeInfo == null) {
                        // No type constraint, always add unless in variable declaration
                        shouldAdd = !inVariableDeclaration;
                    } else {
                        // We have a type constraint, check compatibility
                        TypeInfo sourceTypeInfo = parseTypeString(inferredType);
                        shouldAdd = isValidAssignment(targetTypeInfo, sourceTypeInfo);
                    }
                    
                    if (shouldAdd) {
                        result.addElement(LookupElementBuilder.create(varName)
                            .withPresentableText(varName)
                            .withTypeText(inferredType)
                            .withIcon(AllIcons.Nodes.Variable)
                            .withBoldness(true));
                        foundVars.add(varName);
                        LOG.info("➕ [addScannedCompletions] Added untyped variable with inferred type: " + varName);
                        LOG.warn("🚨 [DEBUG] Suggestion Added - Variable: '" + varName + "', Inferred Type: '" + inferredType + "'");
                    } else {
                        LOG.info("⏩ [addScannedCompletions] Skipped untyped variable " + varName + " - inferred type " + 
                                 inferredType + " doesn't match expected " + expectedType + " or in variable declaration context");
                        LOG.warn("🚨 [DEBUG] Suggestion Skipped - Variable: '" + varName + 
                                 "', Inferred Type: '" + inferredType + "', Expected Type: '" + expectedType + 
                                 "', In Variable Declaration: " + inVariableDeclaration);
                    }
                } else {
                    // MODIFIED: For variables without a type, always include them unless we're in a variable declaration
                    boolean shouldAdd = !inVariableDeclaration;
                    
                    // If there's a type constraint and it's "bool", we might still add untyped variables
                    // as they could be booleans
                    if (targetTypeInfo != null && "bool".equals(targetTypeInfo.baseType)) {
                        shouldAdd = true;
                    }
                    
                    if (shouldAdd) {
                        result.addElement(LookupElementBuilder.create(varName)
                            .withPresentableText(varName)
                            .withTypeText("unknown")
                            .withIcon(AllIcons.Nodes.Variable)
                            .withBoldness(true));
                        foundVars.add(varName);
                        LOG.info("➕ [addScannedCompletions] Added untyped variable without known type: " + varName);
                        LOG.warn("🚨 [DEBUG] Suggestion Added - Variable: '" + varName + "', Type: 'unknown'");
                    } else {
                        LOG.info("⏩ [addScannedCompletions] Skipped untyped variable " + varName + " - unknown type doesn't match expected " + 
                                expectedType + " or in variable declaration context");
                        LOG.warn("🚨 [DEBUG] Suggestion Skipped - Variable: '" + varName + "', Unknown Type, Expected: '" + expectedType + "'");
                    }
                }
            }
        }
        
        // 3. Match direct assignments to find previously declared variables: varName = value
        Pattern assignmentPattern = Pattern.compile("(?:^|\\s)([a-zA-Z_]\\w*)\\s*=[^=<>!]");
        Matcher assignmentMatcher = assignmentPattern.matcher(documentText);
        
        while (assignmentMatcher.find()) {
            String varName = assignmentMatcher.group(1);
            
            // Skip reserved words and variables we've already found
            if (varName.equals("var") || varName.equals("varip") || foundVars.contains(varName)) {
                continue;
            }
            
            // Try to infer type from the assignment
            String inferredType = inferTypeFromAssignment(documentText, varName);
            
            if (inferredType != null) {
                // If we already have a type for this variable but it differs, we may have a reassignment
                // In this case we keep the first type unless the new one is more specific
                if (!varTypeMap.containsKey(varName)) {
                    varTypeMap.put(varName, inferredType);
                }
                
                LOG.info("🔍 [addScannedCompletions] Found variable from assignment: " + varName + " with type: " + inferredType);
                
                // MODIFIED: Always add the variable unless we're in a variable declaration context
                // Or if we're in a typed context, check compatibility
                boolean shouldAdd = false;
                
                if (targetTypeInfo == null) {
                    // No type constraint, always add unless in variable declaration
                    shouldAdd = !inVariableDeclaration;
                } else {
                    // We have a type constraint, check compatibility
                    TypeInfo sourceTypeInfo = parseTypeString(inferredType);
                    shouldAdd = isValidAssignment(targetTypeInfo, sourceTypeInfo);
                }
                
                if (shouldAdd) {
                    result.addElement(LookupElementBuilder.create(varName)
                        .withPresentableText(varName)
                        .withTypeText(inferredType)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withBoldness(true));
                    foundVars.add(varName);
                    LOG.info("➕ [addScannedCompletions] Added variable from assignment: " + varName);
                    LOG.warn("🚨 [DEBUG] Suggestion Added - Variable: '" + varName + "', Inferred Type: '" + inferredType + "'");
                } else {
                    LOG.info("⏩ [addScannedCompletions] Skipped variable from assignment " + varName + 
                             " - type " + inferredType + " doesn't match expected " + expectedType + " or in variable declaration context");
                    LOG.warn("🚨 [DEBUG] Suggestion Skipped - Variable: '" + varName + 
                            "', Inferred Type: '" + inferredType + "', Expected Type: '" + expectedType + "'");
                }
            } else {
                // MODIFIED: For variables without a type, always include them unless we're in a variable declaration
                boolean shouldAdd = !inVariableDeclaration;
                
                // If there's a type constraint and it's "bool", we might still add untyped variables
                if (targetTypeInfo != null && "bool".equals(targetTypeInfo.baseType)) {
                    shouldAdd = true;
                }
                
                if (shouldAdd) {
                    result.addElement(LookupElementBuilder.create(varName)
                        .withPresentableText(varName)
                        .withTypeText("unknown")
                        .withIcon(AllIcons.Nodes.Variable)
                        .withBoldness(true));
                    foundVars.add(varName);
                    LOG.info("➕ [addScannedCompletions] Added variable from assignment without known type: " + varName);
                    LOG.warn("🚨 [DEBUG] Suggestion Added - Variable: '" + varName + "', Type: 'unknown'");
                } else {
                    LOG.info("⏩ [addScannedCompletions] Skipped variable " + varName + " - unknown type doesn't match expected " + 
                             expectedType + " or in variable declaration context");
                    LOG.warn("🚨 [DEBUG] Suggestion Skipped - Variable: '" + varName + 
                            "', Unknown Type, Expected: '" + expectedType + "'");
                }
            }
        }
        
        // 4. Match function declarations: function/method name(params)
        Pattern functionPattern = Pattern.compile("(?:method|function)\\s+([a-zA-Z_]\\w*)\\s*\\(");
        Matcher functionMatcher = functionPattern.matcher(documentText);
        
        while (functionMatcher.find()) {
            String funcName = functionMatcher.group(1);
            
            // MODIFIED: Always include functions unless we're in a variable declaration
            // And if there's a type constraint, only include them if expected type is "function"
            boolean shouldAdd = !inVariableDeclaration;
            
            // If we have a specific type constraint, only include functions if it's a function type
            if (targetTypeInfo != null && !"function".equals(targetTypeInfo.baseType)) {
                shouldAdd = false;
            }
            
            if (shouldAdd) {
                result.addElement(LookupElementBuilder.create(funcName)
                    .withPresentableText(funcName)
                    .withTypeText("function")
                    .withIcon(AllIcons.Nodes.Function)
                    .withBoldness(true));
                LOG.info("➕ [addScannedCompletions] Added function: " + funcName);
            } else {
                LOG.info("⏩ [addScannedCompletions] Skipped function " + funcName + 
                        " - not applicable in the current context");
            }
        }
        
        // Summary of variables found by type for debugging
        for (Map.Entry<String, String> entry : varTypeMap.entrySet()) {
            LOG.info("📊 [addScannedCompletions] Variable type map: " + entry.getKey() + " => " + entry.getValue());
        }
    }
    
    /**
     * Helper method to check if text contains a pattern
     */
    private boolean textContains(String text, String pattern) {
        return Pattern.compile(pattern).matcher(text).find();
    }
    
    /**
     * Helper method to infer variable type from assignment
     */
    private String inferTypeFromAssignment(String documentText, String varName) {
        // Find the most recent assignment to this variable
        Pattern assignmentPattern = Pattern.compile(Pattern.quote(varName) + "\\s*=\\s*([^\\n;]+)");
        Matcher assignmentMatcher = assignmentPattern.matcher(documentText);
        
        String lastValue = null;
        while (assignmentMatcher.find()) {
            lastValue = assignmentMatcher.group(1).trim();
        }
        
        if (lastValue == null) {
            LOG.warn("🚨 [DEBUG] Type Inference Failed - Variable: '" + varName + "', No assignment value found");
            return null;
        }
        
        LOG.info("🔍 [inferTypeFromAssignment] Analyzing value: '" + lastValue + "' for variable: " + varName);
        
        // Check for namespace-based assignments (color.red, array.new, etc.)
        Pattern namespacePattern = Pattern.compile("^(\\w+)\\.");
        Matcher namespaceMatcher = namespacePattern.matcher(lastValue);
        if (namespaceMatcher.find()) {
            String namespace = namespaceMatcher.group(1);
            if (namespace.equals("color")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'color' (from namespace)");
                return "color";
            }
            else if (namespace.equals("array")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'array' (from namespace)");
                return "array";
            }
            else if (namespace.equals("matrix")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'matrix' (from namespace)");
                return "matrix";
            }
            else if (namespace.equals("map")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'map' (from namespace)");
                return "map";
            }
            else if (namespace.equals("table")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'table' (from namespace)");
                return "table";
            }
            else if (namespace.equals("line")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'line' (from namespace)");
                return "line";
            }
            else if (namespace.equals("label")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'label' (from namespace)");
                return "label";
            }
            else if (namespace.equals("box")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'box' (from namespace)");
                return "box";
            }
            else if (namespace.equals("str")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'string' (from namespace)");
                return "string";
            }
            else if (namespace.equals("math")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'float' (from namespace)");
                return "float";
            }
            else if (namespace.equals("int")) {
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'int' (from namespace)");
                return "int";
            }
        }
        
        // Check for boolean literals (true/false)
        if (lastValue.equals("true") || lastValue.equals("false")) {
            LOG.info("🔍 [inferTypeFromAssignment] Boolean literal detected: " + lastValue);
            LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'bool' (from boolean literal)");
            return "bool";
        }
        
        // Check for boolean operators and functions
        if (lastValue.contains(" and ") || lastValue.contains(" or ") || 
            lastValue.matches("^not\\s+.*") || lastValue.matches(".*\\s+crosses\\s+.*") ||
            lastValue.matches(".*\\s+crossover\\s+.*") || lastValue.matches(".*\\s+crossunder\\s+.*")) {
            LOG.info("🔍 [inferTypeFromAssignment] Boolean operation detected: " + lastValue);
            LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'bool' (from boolean operation)");
            return "bool";
        }
        
        // Check for string literals (both ' and " quotes)
        if ((lastValue.startsWith("\"") && lastValue.endsWith("\"")) ||
            (lastValue.startsWith("'") && lastValue.endsWith("'"))) {
            LOG.info("🔍 [inferTypeFromAssignment] String literal detected: " + lastValue);
            LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'string' (from string literal)");
            return "string";
        }
        
        // Check for numeric literals (distinguish between int and float)
        if (lastValue.matches("^-?\\d+$")) {
            LOG.info("🔍 [inferTypeFromAssignment] Integer literal detected: " + lastValue);
            LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'int' (from integer literal)");
            return "int";
        } else if (lastValue.matches("^-?\\d+\\.\\d+$")) {
            LOG.info("🔍 [inferTypeFromAssignment] Float literal detected: " + lastValue);
            LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'float' (from float literal)");
            return "float";
        }
        
        // Check for color literals (#RRGGBB format)
        if (lastValue.matches("^#[0-9A-Fa-f]{6}$")) {
            LOG.info("🔍 [inferTypeFromAssignment] Color literal detected: " + lastValue);
            LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'color' (from color literal)");
            return "color";
        }
        
        // Check for assignment from another variable - look up that variable's type
        if (lastValue.matches("^[a-zA-Z_]\\w*$")) {
            LOG.info("🔍 [inferTypeFromAssignment] Variable reference detected: " + lastValue);
            // Try to find the type of the referenced variable
            Pattern referencedVarPattern = Pattern.compile("(?:var|varip)\\s+(\\w+)\\s+" + Pattern.quote(lastValue) + "\\s*=");
            Matcher referencedVarMatcher = referencedVarPattern.matcher(documentText);
            
            if (referencedVarMatcher.find()) {
                String referencedType = referencedVarMatcher.group(1);
                if (isKnownType(referencedType)) {
                    LOG.info("🔍 [inferTypeFromAssignment] Found referenced variable type: " + referencedType);
                    return referencedType;
                }
            }
            
            // Try infer type recursively for the referenced variable
            String referencedType = inferTypeFromAssignment(documentText, lastValue);
            if (referencedType != null) {
                LOG.info("🔍 [inferTypeFromAssignment] Inferred referenced variable type: " + referencedType);
                return referencedType;
            }
        }
        
        // Check direct function calls that might return known types
        if (lastValue.contains("(") && lastValue.contains(")")) {
            // Check for common function patterns that indicate return types
            if (lastValue.matches("^(barstate\\.is|timeframe\\.is|syminfo\\.session\\.is|.*\\.is_)[a-zA-Z_]+.*")) {
                LOG.info("🔍 [inferTypeFromAssignment] Boolean-returning function detected: " + lastValue);
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'bool' (from boolean function)");
                return "bool";
            }
            
            // Check for numeric functions
            if (lastValue.matches("^(math\\.|ta\\.)[a-zA-Z_]+.*")) {
                LOG.info("🔍 [inferTypeFromAssignment] Numeric function call detected: " + lastValue);
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'float' (from numeric function)");
                return "float";
            }
            
            // Check for string functions
            if (lastValue.matches("^str\\.[a-zA-Z_]+.*")) {
                LOG.info("🔍 [inferTypeFromAssignment] String function call detected: " + lastValue);
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'string' (from string function)");
                return "string";
            }
            
            // Check for comparison operators which return boolean
            if (lastValue.contains(" == ") || lastValue.contains(" != ") || 
                lastValue.contains(" > ") || lastValue.contains(" < ") || 
                lastValue.contains(" >= ") || lastValue.contains(" <= ")) {
                LOG.info("🔍 [inferTypeFromAssignment] Comparison operation detected: " + lastValue);
                LOG.warn("🚨 [DEBUG] Type Inference - Variable: '" + varName + "', Value: '" + lastValue + "', Inferred Type: 'bool' (from comparison)");
                return "bool";
            }
        }
        
        LOG.info("🔍 [inferTypeFromAssignment] Could not determine type for: " + lastValue);
        LOG.warn("🚨 [DEBUG] Type Inference Failed - Variable: '" + varName + "', Value: '" + lastValue + "', Could not determine type");
        return null;
    }
    
    /**
     * Initializes namespace methods for a specific version.
     */
    private static Map<String, String[]> initNamespaceMethodsForVersion(String version, List<String> functionNames) {
        Map<String, String[]> result = new HashMap<>();
        
        LOG.warn("🚨 [initNamespaceMethodsForVersion] Initializing namespace methods for version: " + version);
        
        // Define default namespaces if NAMESPACES is null
        String[] namespacesList = NAMESPACES != null ? NAMESPACES : 
            new String[]{"ta", "math", "array", "str", "color", "chart", "strategy", "syminfo", "request", "ticker"};
        
        // Filter function names that belong to each namespace
        Map<String, List<String>> namespaceMethodsMap = new HashMap<>();
        
        for (String namespace : namespacesList) {
            namespaceMethodsMap.put(namespace, new ArrayList<>());
        }
        
        // Safely process the function names (could be null in some cases)
        if (functionNames != null) {
            // Process the function names from JSON to extract namespace methods
            for (String functionName : functionNames) {
                if (functionName != null && functionName.contains(".")) {
                    String[] parts = functionName.split("\\.", 2);
                    if (parts.length == 2 && namespaceMethodsMap.containsKey(parts[0])) {
                        String namespace = parts[0];
                        String method = parts[1];
                        
                        // Add the method to the appropriate namespace list
                        namespaceMethodsMap.get(namespace).add(method);
                        LOG.info("🔍 [initNamespaceMethodsForVersion] Added method '" + method + 
                                "' to namespace '" + namespace + "' from function '" + functionName + "'");
                    }
                }
            }
        } else {
            LOG.warn("🚨 [initNamespaceMethodsForVersion] Function names list is null!");
        }
        
        // Convert lists to arrays for the final map
        for (Map.Entry<String, List<String>> entry : namespaceMethodsMap.entrySet()) {
            String namespace = entry.getKey();
            List<String> methods = entry.getValue();
            
            // Sort the methods alphabetically for better presentation (safely)
            if (methods != null) {
                try {
                    Collections.sort(methods);
                } catch (Exception e) {
                    LOG.warn("🚨 [initNamespaceMethodsForVersion] Failed to sort methods for namespace '" + 
                            namespace + "': " + e.getMessage());
                }
            }
            
            result.put(namespace, methods != null ? methods.toArray(new String[0]) : new String[0]);
            LOG.info("🔍 [initNamespaceMethodsForVersion] Namespace '" + namespace + 
                    "' has " + (methods != null ? methods.size() : 0) + " methods");
        }
        
        LOG.warn("🚨 [initNamespaceMethodsForVersion] Completed namespace methods initialization for version: " + version);
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
            
            LOG.warn(">>>>>> Installing PineScript CompletionAutoPopupHandler");
            
            typedAction.setupRawHandler(new TypedActionHandler() {
        @Override
                public void execute(@NotNull Editor editor, char c, @NotNull DataContext dataContext) {
                    if (oldHandler != null) {
                        oldHandler.execute(editor, c, dataContext);
                    }
                    
                    // Bail early if not in a Pine Script file
                    Project project = editor.getProject();
                    if (project == null) return;
                    
                    // Get document and current offset
                    Document document = editor.getDocument();
                    int offset = editor.getCaretModel().getOffset();
                    if (offset <= 0) return;
                    
                    String documentText = document.getText();
                    
                    // Skip auto-popup if inside a string
                    if (isInsideString(documentText, offset)) {
                        return;
                    }
                    
                    // Trigger auto popup when a dot is typed - this is critical for namespace members
                    if (c == '.') {
                        LOG.warn(">>>>>> DOT TYPED at offset " + offset + ", triggering member lookup");
                        
                        // Find what's before the dot to log debugging info
                        if (offset > 1) {
                            String textBeforeCursor = documentText.substring(0, offset);
                            String namespace = findNamespaceBeforeDot(textBeforeCursor);
                            LOG.warn(">>>>>> Namespace before dot (from handler): " + namespace);
                        }
                        
                        // Force immediate popup - must happen on the UI thread
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (project.isDisposed() || editor.isDisposed()) return;
                            
                            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                            LOG.warn(">>>>>> Invoking member lookup for editor");
                            
                            // Try multiple auto-popup methods for redundancy
                            try {
                                // First try direct member lookup (most reliable)
                                AutoPopupController controller = AutoPopupController.getInstance(project);
                                controller.autoPopupMemberLookup(editor, null);
                                
                                // Also schedule auto-popup for good measure
                                controller.scheduleAutoPopup(editor);
                                
                                // Schedule a backup approach with a slight delay
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    try {
                                        if (project.isDisposed() || editor.isDisposed()) return;
                                        
                                        LOG.warn(">>>>>> Backup: Scheduling general completion popup");
                                        controller.scheduleAutoPopup(editor);
                                        
                                        // Manually attempt to insert a dummy character and delete it to force a refresh
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            try {
                                                if (project.isDisposed() || editor.isDisposed()) return;
                                                
                                                LOG.warn(">>>>>> Last resort: Forcing code completion refresh");
                                                controller.scheduleAutoPopup(editor);
                                            } catch (Exception e) {
                                                LOG.warn("Error in last resort code completion: " + e.getMessage());
                                            }
                                        }, ModalityState.current(), project.getDisposed());
                                    } catch (Exception e) {
                                        LOG.warn("Error in backup code completion: " + e.getMessage());
                                    }
                                }, ModalityState.current(), project.getDisposed());
                            } catch (Exception e) {
                                LOG.error("Error showing code completion: " + e.getMessage(), e);
                            }
                        }, ModalityState.current());
                        return;
                    }
                    
                    // NEW: Check if we're typing after a dot (e.g., "request.s" when typing 's')
                    // This handles the case when you delete text after a dot and start typing again
                    if (Character.isJavaIdentifierPart(c) && offset > 1) {
                        // Check if there's a dot before our current position
                        String textBeforeCursor = documentText.substring(0, offset - 1); // Exclude the character we just typed
                        int lastDotPos = textBeforeCursor.lastIndexOf('.');
                        
                        // Check if we found a dot and we're typing right after it or 
                        // after some identifier characters that follow the dot
                        if (lastDotPos >= 0) {
                            // Check if there are only valid identifier characters between the dot and our current position
                            String textAfterDot = textBeforeCursor.substring(lastDotPos + 1);
                            boolean isValidContext = true;
                            
                            for (char ch : textAfterDot.toCharArray()) {
                                if (!Character.isJavaIdentifierPart(ch)) {
                                    isValidContext = false;
                                    break;
                                }
                            }
                            
                            if (isValidContext) {
                                // We're typing a valid identifier character after a dot or after text that follows a dot
                                String potentialNamespace = findNamespaceAtPosition(textBeforeCursor, lastDotPos);
                                LOG.warn(">>>>>> TYPING AFTER DOT: Character '" + c + "' at offset " + offset + 
                                         ", potential namespace: " + potentialNamespace);
                                
                                // Only trigger completion if we have a valid namespace
                                if (potentialNamespace != null) {
                                    // Calculate the complete text after the dot to use for filtering
                                    String completeFilterText = textAfterDot + c;
                                    LOG.warn(">>>>>> Complete filter text: '" + completeFilterText + "'");
                                    
                                    // Trigger popup with a slight delay to ensure the character is processed
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        try {
                                            PsiDocumentManager.getInstance(project).commitDocument(document);
                                            LOG.warn(">>>>>> Triggering completion popup after typing character after dot");
                                            
                                            // Create a custom prefix lookup that will force the correct filtering
                                            AutoPopupController controller = AutoPopupController.getInstance(project);
                                            
                                            // Trigger the proper completion popup
                                            controller.autoPopupMemberLookup(editor, null);
                                        } catch (Exception e) {
                                            LOG.warn("Error triggering completion after dot: " + e.getMessage());
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                    }
                    
                    // Trigger completion popup for space inside function call or comma in function call (not header)
                    if (isInsideFunctionCall(documentText, offset) && (c == ' ' || c == ',' || c == '(')) {
                        // Use the project we already have
                        if (project != null) {
                            LOG.warn(">>>>>> COMMA/SPACE IN FUNCTION: Triggering parameter info at offset " + offset);
                            
                            // More aggressive approach to force parameter info popup
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    // First commit any changes to ensure PSI is up to date
                                    PsiDocumentManager.getInstance(project).commitDocument(document);
                                    
                                    // Force parameter info - most important for function arguments
                                    AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            });
                        }
                        return;
                    }
                    
                    // Improved function argument detection - check for ANY typing inside a function call
                    if (isInsideFunctionCall(documentText, offset)) {
                        if (project != null) {
                            LOG.warn(">>>>>> TYPING IN FUNCTION: Triggering parameter info at offset " + offset);
                            
                            // Use short delay to let the character be processed
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    // First commit any changes to ensure PSI is up to date
                                    PsiDocumentManager.getInstance(project).commitDocument(document);
                                    
                                    // Directly show parameter info
                                    AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            });
                        }
                        return;
                    }
                }
            

            });
        }
    }

    /**
     * Finds the namespace at a given dot position in the text.
     * Works similar to findNamespaceBeforeDot but can be used when the dot is not at the end.
     */
    private static String findNamespaceAtPosition(String text, int dotPosition) {
        if (dotPosition < 0 || dotPosition >= text.length() || text.charAt(dotPosition) != '.') {
            return null;
        }
        
        // Find the last non-identifier character before the dot
        int lastNonWordChar = -1;
        for (int i = dotPosition - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                // Allow dots for qualified identifiers
                if (c != '.') {
                    lastNonWordChar = i;
                    break;
                }
            }
        }
        
        String word = text.substring(lastNonWordChar + 1, dotPosition);
        LOG.warn("🔍 [findNamespaceAtPosition] Found namespace at position " + dotPosition + ": \"" + word + "\"");
        
        // Validate that this is a proper identifier
        if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) {
            LOG.warn("🔍 [findNamespaceAtPosition] Invalid namespace: \"" + word + "\"");
            return null;
        }
        
        return word;
    }

    /**
     * Checks if a position in the text is inside a comment.
     * @param text The text to check
     * @param position The position to check
     * @return true if the position is inside a comment, false otherwise
     */
    private static boolean isInsideComment(String text, int position) {
        // Look for // or /* comment markers before the position
        int pos = position;
        while (pos >= 0) {
            // Check for line comment //
            if (pos > 0 && text.charAt(pos - 1) == '/' && text.charAt(pos) == '/') {
                // Found a line comment, now check if there's a newline between this and the position
                int newlinePos = text.indexOf('\n', pos);
                if (newlinePos == -1 || newlinePos > position) {
                    // No newline found or newline is after our position, so we're in a comment
                    LOG.warn("🔍 [isInsideComment] Position " + position + " is inside a line comment starting at " + pos);
                    return true;
                }
            }
            
            // Check for block comment start /*
            if (pos > 0 && text.charAt(pos - 1) == '/' && text.charAt(pos) == '*') {
                // Found block comment start, check if there's a block comment end between this and the position
                int commentEnd = text.indexOf("*/", pos);
                if (commentEnd == -1 || commentEnd + 1 > position) {
                    // No comment end found or comment end is after our position, so we're in a comment
                    LOG.warn("🔍 [isInsideComment] Position " + position + " is inside a block comment starting at " + pos);
                    return true;
                }
            }
            
            pos--;
        }
        
        return false;
    }

    /**
     * Returns a map of suggested values for a specified type
     */
    private Map<String, String> getValueSuggestionsForTypeWithPrefix(String type, String variableName, String prefix, int offset) {
        Map<String, String> suggestions = new HashMap<>();
        String normalizedType = normalizeType(type.toLowerCase());
        
        LOG.warn("🚨 [getValueSuggestionsForTypeWithPrefix] Getting suggestions for type: " + type + ", variable: " + variableName);
        
        // Always include certain basic suggestions based on type
        
        // Boolean suggestions
        if (normalizedType.contains("bool")) {
            suggestions.put("true", "boolean");
            suggestions.put("false", "boolean");
            suggestions.put("na", "boolean");
        }
        
        // String suggestions
        else if (normalizedType.contains("string")) {
            suggestions.put("\"\"", "string");
            suggestions.put("na", "string");
            
            // Add variable-name based suggestion
            if (variableName != null && !variableName.isEmpty()) {
                suggestions.put("\"" + variableName + "\"", "string");
            }
        }
        
        // Color suggestions - these are universal constants
        else if (normalizedType.contains("color")) {
            // Include just the basic color suggestions - don't hardcode an exhaustive list
            suggestions.put("color.new(255, 255, 255, 0)", "color");
            suggestions.put("color.rgb(255, 255, 255)", "color");
            suggestions.put("na", "color");
            
            // Let the JSON-loaded color constants fill in the rest
        }
        
        // Integer/float suggestions
        else if (normalizedType.contains("int") || normalizedType.contains("float")) {
            suggestions.put("0", normalizedType.contains("int") ? "integer" : "float");
            suggestions.put("1", normalizedType.contains("int") ? "integer" : "float");
            suggestions.put("-1", normalizedType.contains("int") ? "integer" : "float");
            suggestions.put("na", normalizedType);
            
            // Include the common OHLC variables for 'price' related variables
            if (variableName != null && variableName.toLowerCase().contains("price")) {
                suggestions.put("close", "float");
                suggestions.put("open", "float");
                suggestions.put("high", "float");
                suggestions.put("low", "float");
            }
        }
        
        // Use the default version for simplicity
        String version = DEFAULT_VERSION;
        
        // Add constants with matching types from the loaded data
        Map<String, String> constantTypes = CONSTANT_TYPES.getOrDefault(version, new HashMap<>());
        for (Map.Entry<String, String> entry : constantTypes.entrySet()) {
            String constant = entry.getKey();
            String constantType = entry.getValue();
            
            // If the constant type matches our target type and it's not already in suggestions, add it
            if (constantType.toLowerCase().contains(normalizedType) && !suggestions.containsKey(constant)) {
                suggestions.put(constant, normalizedType);
                LOG.info("🔍 [getValueSuggestionsForTypeWithPrefix] Added constant '" + constant + 
                         "' as suggestion for type '" + normalizedType + "'");
            }
        }
        
        return suggestions;
    }
    
    /**
     * Normalizes a type string to simplify type checking
     */
    private String normalizeType(String type) {
        if (type == null) return "";
        // Basic normalization - strip whitespace and simplify
        String normalized = type.trim().toLowerCase();
        // Remove array/series specifiers but retain the base type
        if (normalized.startsWith("array<") && normalized.endsWith(">")) {
            return "array_" + normalized.substring(6, normalized.length() - 1);
        }
        if (normalized.startsWith("series(") && normalized.endsWith(")")) {
            return normalized.substring(7, normalized.length() - 1) + "_series";
        }
        return normalized;
    }
    
    /**
     * Extracts the element type from an array type specification
     */
    private String extractArrayElementType(String type) {
        if (type == null) return "";
        // Handle explicit array<type> syntax
        if (type.contains("<") && type.contains(">")) {
            int start = type.indexOf('<') + 1;
            int end = type.lastIndexOf('>');
            if (start < end) {
                return type.substring(start, end).trim();
            }
        }
        // Try to infer from naming conventions
        if (type.toLowerCase().contains("float")) return "float";
        if (type.toLowerCase().contains("int")) return "int";
        if (type.toLowerCase().contains("bool")) return "bool";
        if (type.toLowerCase().contains("string")) return "string";
        if (type.toLowerCase().contains("color")) return "color";
        return "";
    }

    // Fix compilation error at the end of the file
    private String getColorNameFromHex(String hex) {
        if (hex == null || hex.isEmpty() || !hex.startsWith("#")) {
            return "";
        }
        
        // Convert common colors
        if (hex.equalsIgnoreCase("#FF0000")) return "color.red";
        if (hex.equalsIgnoreCase("#00FF00")) return "color.green";
        if (hex.equalsIgnoreCase("#0000FF")) return "color.blue";
        if (hex.equalsIgnoreCase("#FFFF00")) return "color.yellow";
        if (hex.equalsIgnoreCase("#00FFFF")) return "color.cyan";
        if (hex.equalsIgnoreCase("#FF00FF")) return "color.magenta";
        if (hex.equalsIgnoreCase("#FFFFFF")) return "color.white";
        if (hex.equalsIgnoreCase("#000000")) return "color.black";
        
        // Special cases
        if (hex.equalsIgnoreCase("#FFA500")) return "color.orange";
        if (hex.equalsIgnoreCase("#800080")) return "color.purple";
        if (hex.equalsIgnoreCase("#A52A2A")) return "color.brown";
        
        // Default to just using color.new
        if (hex.toLowerCase().contains("color")) return "color";
        return "";
    }

    /**
     * Loads return types from functions.json definition file.
     * @param version The Pine Script version
     * @param filename The JSON file name (functions.json)
     * @return A map of function names to their return types
     */
    private static Map<String, String> loadReturnTypesFromDefinitionFile(String version, String filename) {
        Map<String, String> returnTypes = new HashMap<>();
        String resourcePath = "/definitions/v" + version + "/" + filename;
        
        try (InputStream is = PineScriptCompletionContributor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("❌ Resource not found: " + resourcePath);
                return returnTypes;
            }
            
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            LOG.info("📖 [loadReturnTypesFromDefinitionFile] Processing functions from: " + resourcePath);
            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                if (item.has("name") && (item.has("returnType") || item.has("syntax"))) {
                    String name = item.getString("name");
                    // Remove trailing parentheses if they exist
                    if (name.endsWith("()")) {
                        name = name.substring(0, name.length() - 2);
                    }
                    
                    String returnType;
                    if (item.has("returnType")) {
                        returnType = item.getString("returnType");
                        LOG.info("✅ [loadReturnTypesFromDefinitionFile] Found return type from returnType field: " + name + " -> " + returnType);
                    } else {
                        // Extract return type from syntax field
                        String syntax = item.getString("syntax");
                        int arrowIndex = syntax.lastIndexOf("→");
                        if (arrowIndex != -1) {
                            returnType = syntax.substring(arrowIndex + 1).trim();
                            LOG.info("✅ [loadReturnTypesFromDefinitionFile] Extracted return type from syntax: " + name + " -> " + returnType);
                        } else {
                            returnType = "any";
                            LOG.warn("⚠️ [loadReturnTypesFromDefinitionFile] No return type found for: " + name);
                        }
                    }
                    returnTypes.put(name, returnType);
                } else {
                    LOG.warn("⚠️ [loadReturnTypesFromDefinitionFile] Missing required fields in function definition at index " + i);
                }
            }
            
            LOG.info("📊 [loadReturnTypesFromDefinitionFile] Loaded return types for " + returnTypes.size() + " functions");
            // Log first few entries as sample
            int count = 0;
            for (Map.Entry<String, String> entry : returnTypes.entrySet()) {
                if (count++ < 5) {
                    LOG.info("📝 Sample return type: " + entry.getKey() + " -> " + entry.getValue());
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            LOG.error("❌ Error loading return types from " + resourcePath, e);
        } catch (Exception e) {
            LOG.error("❌ Error processing return types from " + resourcePath, e);
            e.printStackTrace();
        }
        
        return returnTypes;
    }

    private static Map<String, String> loadTypesFromDefinitionFile(String version, String filename) {
        Map<String, String> types = new HashMap<>();
        
        try {
            String resourcePath = "/definitions/" + version + "/" + filename;
            LOG.info("📝 [loadTypesFromDefinitionFile] Loading types from " + resourcePath);
            
            InputStream is = PineScriptCompletionContributor.class.getResourceAsStream(resourcePath);
            if (is == null) {
                LOG.warn("⚠️ Resource not found: " + resourcePath);
                return types;
            }
            
            // Read the content as a string
            String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
            
            try {
                // Try to parse as JSON object
                if (content.startsWith("{")) {
                    JSONObject json = new JSONObject(content);
                    JSONArray items = json.getJSONArray("items");
                    LOG.info("✅ Found " + items.length() + " typed items");
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        if (item.has("name") && item.has("type")) {
                            String name = item.getString("name");
                            String type = item.getString("type");
                            types.put(name, type);
                        } else {
                            LOG.warn("⚠️ Missing required fields in definition at index " + i);
                        }
                    }
                } 
                // Try to parse as JSON array
                else if (content.startsWith("[")) {
                    JSONArray jsonArray = new JSONArray(content);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject item = jsonArray.getJSONObject(i);
                        if (item.has("name") && item.has("type")) {
                            String name = item.getString("name");
                            String type = item.getString("type");
                            types.put(name, type);
                        } else {
                            LOG.warn("⚠️ Missing required fields in definition at index " + i);
                        }
                    }
                } else {
                    LOG.error("❌ Invalid JSON format in " + filename + ". Content starts with: " + 
                             (content.length() > 20 ? content.substring(0, 20) + "..." : content));
                    return types;
                }
            } catch (Exception e) {
                LOG.error("❌ Error parsing JSON from " + filename, e);
                return types;
            }
            
            LOG.info("✅ Loaded " + types.size() + " types from " + filename);
            
            // Log some sample entries for debugging
            int count = 0;
            for (Map.Entry<String, String> entry : types.entrySet()) {
                if (count < 3) {
                    LOG.info("   - " + entry.getKey() + ": " + entry.getValue());
                    count++;
                } else {
                    break;
                }
            }
            
        } catch (Exception e) {
            LOG.error("❌ Error loading types from " + filename, e);
        }
        
        return types;
    }

    /**
     * Calculate which parameter index we're typing based on comma positions
     * @param text The text before the cursor position
     * @return The index of the parameter (0-based)
     */
    private static int getParamIndexFromText(String text) {
        // Find the last open parenthesis
        int lastOpenParenPos = text.lastIndexOf('(');
        if (lastOpenParenPos >= 0) {
            String textInsideParams = text.substring(lastOpenParenPos + 1);
            // Count commas to determine parameter index
            int paramIndex = (int) textInsideParams.chars().filter(ch -> ch == ',').count();
            
            LOG.info("Parameter index: " + paramIndex + ", text inside params: '" + 
                    (textInsideParams.length() > 20 ? "..." + textInsideParams.substring(textInsideParams.length() - 20) : textInsideParams) + "'");
            
            return paramIndex;
        }
        return 0;
    }
}
