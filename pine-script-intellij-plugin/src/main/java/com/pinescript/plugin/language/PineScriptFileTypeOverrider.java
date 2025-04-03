package com.pinescript.plugin.language;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PineScriptFileTypeOverrider implements FileTypeOverrider {
    @Override
    public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
        String extension = file.getExtension();
        if (extension != null && (extension.equals("pine") || extension.equals("ps") || extension.equals("pinescript"))) {
            return PineScriptFileType.INSTANCE;
        }
        return null;
    }
} 