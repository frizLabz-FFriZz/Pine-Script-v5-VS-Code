package com.pinescript.plugin.completion;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that provides data about Pine Script built-in functions, their parameters, and return types.
 */
public class PineScriptFunctionData {
    
    private static final Map<String, FunctionInfo[]> FUNCTION_MAP = initFunctionMap();
    
    /**
     * Class representing information about a Pine Script function.
     */
    public static class FunctionInfo {
        private final String name;
        private final String[] parameters;
        private final String returnType;
        private final String description;
        
        public FunctionInfo(String name, String[] parameters, String returnType, String description) {
            this.name = name;
            this.parameters = parameters;
            this.returnType = returnType;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public String[] getParameters() {
            return parameters;
        }
        
        public String getReturnType() {
            return returnType;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Get function information for a specific function name.
     * 
     * @param functionName The name of the function
     * @return Array of FunctionInfo objects or null if not found
     */
    public static FunctionInfo[] getFunctionInfo(String functionName) {
        return FUNCTION_MAP.get(functionName);
    }
    
    /**
     * Get the entire function map.
     * 
     * @return Map of function names to their information
     */
    public static Map<String, FunctionInfo[]> getFunctionMap() {
        return FUNCTION_MAP;
    }
    
    /**
     * Initialize the function map with Pine Script built-in functions.
     * 
     * @return The initialized function map
     */
    private static Map<String, FunctionInfo[]> initFunctionMap() {
        Map<String, FunctionInfo[]> map = new HashMap<>();
        
        // Technical analysis functions
        map.put("ta.sma", new FunctionInfo[] {
            new FunctionInfo("ta.sma", 
                new String[] {"source", "length"}, 
                "series float", 
                "Simple Moving Average")
        });
        
        map.put("ta.ema", new FunctionInfo[] {
            new FunctionInfo("ta.ema", 
                new String[] {"source", "length"}, 
                "series float", 
                "Exponential Moving Average")
        });
        
        map.put("ta.rsi", new FunctionInfo[] {
            new FunctionInfo("ta.rsi", 
                new String[] {"source", "length"}, 
                "series float", 
                "Relative Strength Index")
        });
        
        map.put("ta.macd", new FunctionInfo[] {
            new FunctionInfo("ta.macd", 
                new String[] {"source", "fastLength", "slowLength", "signalLength"}, 
                "series float", 
                "Moving Average Convergence/Divergence")
        });
        
        // Chart functions
        map.put("plot", new FunctionInfo[] {
            new FunctionInfo("plot", 
                new String[] {"series", "title", "color", "linewidth", "style", "trackprice", "histbase", "offset", "join", "editable", "show_last", "display"}, 
                "plot", 
                "Plot a series on the chart")
        });
        
        map.put("hline", new FunctionInfo[] {
            new FunctionInfo("hline", 
                new String[] {"price", "title", "color", "linestyle", "linewidth", "editable"}, 
                "hline", 
                "Draw a horizontal line at a given price level")
        });
        
        // String functions
        map.put("str.format", new FunctionInfo[] {
            new FunctionInfo("str.format", 
                new String[] {"formatString", "arg1", "arg2", "arg3", "arg4", "arg5", "arg6", "arg7", "arg8", "arg9", "arg10"}, 
                "string", 
                "Format a string with placeholders")
        });
        
        // Math functions
        map.put("math.abs", new FunctionInfo[] {
            new FunctionInfo("math.abs", 
                new String[] {"value"}, 
                "float", 
                "Absolute value of a number")
        });
        
        map.put("math.round", new FunctionInfo[] {
            new FunctionInfo("math.round", 
                new String[] {"value", "precision"}, 
                "float", 
                "Round a number to a specified precision")
        });
        
        // Color functions
        map.put("color.rgb", new FunctionInfo[] {
            new FunctionInfo("color.rgb", 
                new String[] {"red", "green", "blue", "transparency"}, 
                "color", 
                "Create a color from RGB values")
        });
        
        map.put("color.new", new FunctionInfo[] {
            new FunctionInfo("color.new", 
                new String[] {"color", "transparency"}, 
                "color", 
                "Create a color with transparency")
        });
        
        // Array functions
        map.put("array.new_float", new FunctionInfo[] {
            new FunctionInfo("array.new_float", 
                new String[] {"size", "initial_value"}, 
                "array<float>", 
                "Create a new array of floats")
        });
        
        map.put("array.push", new FunctionInfo[] {
            new FunctionInfo("array.push", 
                new String[] {"id", "value"}, 
                "void", 
                "Push a value to the end of an array")
        });
        
        // Time functions
        map.put("time", new FunctionInfo[] {
            new FunctionInfo("time", 
                new String[] {"timeframe", "session", "timezone"}, 
                "series int", 
                "Returns the time of the bar's open in UNIX format")
        });
        
        // Trade strategy functions
        map.put("strategy.entry", new FunctionInfo[] {
            new FunctionInfo("strategy.entry", 
                new String[] {"id", "direction", "qty", "limit", "stop", "oca_name", "oca_type", "comment", "alert_message"}, 
                "void", 
                "Enter a trade")
        });
        
        map.put("strategy.exit", new FunctionInfo[] {
            new FunctionInfo("strategy.exit", 
                new String[] {"id", "from_entry", "qty", "qty_percent", "profit", "limit", "loss", "stop", "trail_points", "trail_offset", "oca_name", "comment", "alert_message"}, 
                "void", 
                "Exit a trade")
        });
        
        return map;
    }
} 