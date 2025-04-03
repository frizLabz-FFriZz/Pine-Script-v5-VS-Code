package com.pinescript.plugin.completion;

import java.util.HashMap;
import java.util.Map;

public class PineScriptFunctionData {
    private static final Map<String, FunctionInfo[]> FUNCTION_DATA = initFunctionData();

    public static class FunctionInfo {
        private final String name;
        private final String returnType;
        private final String[] parameters;
        private final String documentation;

        public FunctionInfo(String name, String returnType, String[] parameters, String documentation) {
            this.name = name;
            this.returnType = returnType;
            this.parameters = parameters;
            this.documentation = documentation;
        }

        public String getName() {
            return name;
        }

        public String getReturnType() {
            return returnType;
        }

        public String[] getParameters() {
            return parameters;
        }

        public String getDocumentation() {
            return documentation;
        }
    }

    private static Map<String, FunctionInfo[]> initFunctionData() {
        Map<String, FunctionInfo[]> map = new HashMap<>();
        
        // Add some common Pine Script functions
        map.put("plot", new FunctionInfo[] {
            new FunctionInfo("plot", "plot", 
                new String[] {"series", "title", "color", "linewidth", "style", "trackprice", "histbase", "offset", "join", "editable", "show_last", "display"},
                "Plots a series of data on the chart.")
        });
        
        map.put("ta.sma", new FunctionInfo[] {
            new FunctionInfo("ta.sma", "series float", 
                new String[] {"source", "length"},
                "Calculates the Simple Moving Average of a series.")
        });
        
        map.put("ta.ema", new FunctionInfo[] {
            new FunctionInfo("ta.ema", "series float", 
                new String[] {"source", "length"},
                "Calculates the Exponential Moving Average of a series.")
        });
        
        map.put("ta.rsi", new FunctionInfo[] {
            new FunctionInfo("ta.rsi", "series float", 
                new String[] {"source", "length"},
                "Calculates the Relative Strength Index of a series.")
        });
        
        map.put("input", new FunctionInfo[] {
            new FunctionInfo("input.float", "input float", 
                new String[] {"defval", "title", "tooltip", "inline", "group", "display", "confirm"},
                "Creates a float input option in the settings."),
            new FunctionInfo("input.int", "input int", 
                new String[] {"defval", "title", "minval", "maxval", "step", "tooltip", "inline", "group", "display", "confirm"},
                "Creates an integer input option in the settings."),
            new FunctionInfo("input.bool", "input bool", 
                new String[] {"defval", "title", "tooltip", "inline", "group", "display", "confirm"},
                "Creates a boolean input option in the settings.")
        });
        
        map.put("array.new_float", new FunctionInfo[] {
            new FunctionInfo("array.new_float", "array<float>", 
                new String[] {"size", "initial_value"},
                "Creates a new array of float type with a specified size and initial value.")
        });
        
        map.put("math.max", new FunctionInfo[] {
            new FunctionInfo("math.max", "series float", 
                new String[] {"value1", "value2"},
                "Returns the larger of two values.")
        });
        
        // Add more Pine Script functions as needed
        
        return map;
    }

    public static FunctionInfo[] getFunctionInfo(String functionName) {
        return FUNCTION_DATA.get(functionName);
    }
    
    public static Map<String, FunctionInfo[]> getFunctionMap() {
        return FUNCTION_DATA;
    }
} 