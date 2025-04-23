package com.pinescript.plugin.language;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Register the Pine Script file type with appropriate extensions.
 */
public class PineScriptFileTypeFactory extends FileTypeFactory {
    @Override
    public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(PineScriptFileType.INSTANCE, "pine;ps;pinescript");
    }
} 