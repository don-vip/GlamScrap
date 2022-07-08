package com.github.donvip.archscrap.uploadtools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import com.github.donvip.archscrap.ArchScrap;
import com.github.donvip.archscrap.domain.Fonds;

public abstract class UploadTool {

    private static final Set<String> LOGGED_MESSAGES = new HashSet<>();

    public void writeUploadFile(Fonds f, ArchScrap cityScrap) throws IOException {
        Path dir = Files.createDirectories(cityScrap.getDownloadDir(f).resolve("upload"));
        String filename = getClass().getSimpleName().toLowerCase(Locale.ENGLISH) + '.' + getFileExtension();
        try (OutputStream out = Files.newOutputStream(dir.resolve(filename))) {
            writeContents(f, cityScrap, out);
        }
    }

    protected abstract void writeContents(Fonds f, ArchScrap cityScrap, OutputStream out) throws IOException;

    protected abstract String getFileExtension();

    protected final String getTemplateContents(String templateName) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/templates/" + templateName + ".ftl")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    protected static String cat(String cat) {
        return "[[Category:" + cat + "]]";
    }

    protected static void logOnce(Consumer<String> logOpe, String msg) {
        if (LOGGED_MESSAGES.add(msg)) {
            logOpe.accept(msg);
        }
    }
}
