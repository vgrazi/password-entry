package com.vgrazi.passwordentry;

import com.github.kwhat.jnativehook.NativeLibraryLocator;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;

public class ExtractedNativeLibraryLocator implements NativeLibraryLocator {
    @Override
    public Iterator<File> getLibraries() {
        String extractedLibraryPath = System.getProperty(PasswordEntryApplication.EXTRACTED_NATIVE_LIB_PATH_PROPERTY);
        if (extractedLibraryPath == null || extractedLibraryPath.isBlank()) {
            return Collections.emptyIterator();
        }

        return Collections.singletonList(new File(extractedLibraryPath)).iterator();
    }
}

