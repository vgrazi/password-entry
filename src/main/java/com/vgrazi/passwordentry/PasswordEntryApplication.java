package com.vgrazi.passwordentry;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.awt.event.KeyEvent.*;

/**
 * Introduce native key strokes in your applications, even if Java does not have the focus
 */
@SpringBootApplication
public class PasswordEntryApplication implements NativeKeyListener {

    static final String EXTRACTED_NATIVE_LIB_PATH_PROPERTY = "passwordentry.jnativehook.lib.path";

    // Must be declared before the static initializer below, which uses them
    // (static fields and blocks are initialized in textual order).
    static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    static final boolean IS_MAC = OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    static final boolean IS_WINDOWS = OS_NAME.contains("win");

    @Value("${password-entry.username}")
    private String username;

    @Value("${password-entry.password}")
    private String password;

    @Value("${password-entry.pin}")
    private String pin;

    @Value("${click-span}")
    private long clickSpan;

    /** keyboard = global hotkeys that type for you; clipboard = copy buttons; auto = clipboard on macOS, keyboard elsewhere */
    @Value("${entry-mode:auto}")
    private String entryMode;

    @Value("${clipboard-clear-seconds:20}")
    private int clipboardClearSeconds;

    private boolean useClipboardMode() {
        return switch (entryMode.trim().toLowerCase()) {
            case "clipboard" -> true;
            case "keyboard" -> false;
            default -> IS_MAC;
        };
    }

    private final Robot robot;

    String city = "NYC (240G)";
    String department = "Foundational Frameworks";

    static {
        try {
            java.nio.file.Path nativeLibraryPath = extractNativeLibrary();
            if (nativeLibraryPath != null) {
                // Force JNativeHook to use a locator that returns the extracted file path.
                System.setProperty(EXTRACTED_NATIVE_LIB_PATH_PROPERTY, nativeLibraryPath.toString());
                System.setProperty("jnativehook.lib.locator", ExtractedNativeLibraryLocator.class.getCanonicalName());
                System.out.println("Prepared native library at: " + nativeLibraryPath);
            } else {
                System.out.println("Native library not found in resources");
                }
        } catch (Exception e) {
            System.err.println("Failed to prepare native library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Maps the JVM's os.arch to the folder names used inside the jnativehook jar.
     */
    private static String nativeArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return switch (arch) {
            case "amd64", "x86_64", "x86-64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> arch.startsWith("arm") ? "arm" : arch;
        };
    }

    private static java.nio.file.Path extractNativeLibrary() throws java.io.IOException {
        String osFolder;
        String libName;

        if (IS_WINDOWS) {
            osFolder = "windows";
            libName = "JNativeHook.dll";
        } else if (IS_MAC) {
            osFolder = "darwin";
            libName = "libJNativeHook.dylib";
        } else {
            osFolder = "linux";
            libName = "libJNativeHook.so";
        }

        // Prefer the library bundled in the jnativehook jar for this exact OS/arch
        // (covers both Intel and Apple Silicon Macs), then fall back to /native.
        String[] candidates = {
                "/com/github/kwhat/jnativehook/lib/" + osFolder + "/" + nativeArch() + "/" + libName,
                "/native/" + libName
        };

        for (String resource : candidates) {
            try (java.io.InputStream is = PasswordEntryApplication.class.getResourceAsStream(resource)) {
                if (is == null) {
                    continue;
                }
                java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("jnativehook");
                tempDir.toFile().deleteOnExit();
                java.nio.file.Path libPath = tempDir.resolve(libName);
                java.nio.file.Files.copy(is, libPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                libPath.toFile().deleteOnExit();
                System.out.println("Using native library resource: " + resource);
                return libPath;
            }
        }
        return null;
    }
    public PasswordEntryApplication() throws AWTException {
        robot = new Robot();
        if (IS_MAC) {
            // macOS drops synthetic key events that arrive too quickly back-to-back
            robot.setAutoDelay(10);
        }
    }

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(PasswordEntryApplication.class);
        builder.headless(false).run(args);
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            // Disable JNativeHook logging
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);

            // Set up native library path for Spring Boot JAR
//            setupNativeLibraryPath();

            if (useClipboardMode()) {
                // No global hook / Robot typing, so no OS permission prompts (e.g. macOS Accessibility)
                System.out.println("Starting in clipboard mode");
                java.util.LinkedHashMap<String, String> entries = new java.util.LinkedHashMap<>();
                entries.put("ams\\" + username, "ams\\" + username);
                entries.put("Username", username);
                entries.put("Password", password);
                entries.put("PIN", pin);
                new ClipboardWindow(clipboardClearSeconds).show(entries);
            } else {
                // Register JNativeHook
                register();
                createControlFrame();
            }

            // Keep the application running indefinitely
            synchronized (PasswordEntryApplication.this) {
                try {
                    PasswordEntryApplication.this.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    private void setupNativeLibraryPath() {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String libName;

            if (osName.contains("win")) {
                libName = "JNativeHook.dll";
            } else if (osName.contains("mac")) {
                libName = "libJNativeHook.dylib";
            } else {
                libName = "libJNativeHook.so";
            }

            // Create temporary directory
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("jnativehook");
            tempDir.toFile().deleteOnExit();

            // Load from resources (extracted at build time)
            try (java.io.InputStream is = getClass().getResourceAsStream("/native/" + libName)) {
                if (is != null) {
                    java.nio.file.Path libPath = tempDir.resolve(libName);
                    java.nio.file.Files.copy(is, libPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    libPath.toFile().deleteOnExit();

                    System.setProperty("jnativehook.lib.path", tempDir.toString());
                    System.setProperty("java.library.path", tempDir.toString());
                    System.out.println("Extracted native library to: " + libPath);
                } else {
                    System.out.println("Native library not found in resources, letting JNativeHook handle it");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to setup native library path: " + e.getMessage());
            e.printStackTrace();
        }
    }

    volatile boolean registered;

    /**
     * Registers the native hook for global keyboard events.
     * This method is synchronized and idempotent, i.e. it will only be executed once.
     * It registers the native hook and adds this object as a native key listener.
     * If the registration fails, it throws a RuntimeException.
     */
    private synchronized void register() {
        if (!registered) {
            System.out.println("Registering native hook");
            try {
                GlobalScreen.registerNativeHook();
                GlobalScreen.addNativeKeyListener(this);
                System.out.println("Native hook registered successfully");
            } catch (NativeHookException ex) {
                System.err.println("Failed to register native hook: " + ex.getMessage());
                if (IS_MAC) {
                    System.err.println("On macOS, grant Accessibility (and Input Monitoring) permission to the app " +
                            "running Java (Terminal, iTerm, IntelliJ, or the java binary) under " +
                            "System Settings > Privacy & Security, then restart it.");
                }
                ex.printStackTrace();
                throw new RuntimeException("Failed to register native hook: " + ex.getMessage(), ex);
            }
            registered = true;
        }
    }
    private void createControlFrame() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Password Entry Control");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(200, 100);
            frame.setLocationRelativeTo(null);

            JButton closeButton = new JButton("Close Application");
            closeButton.addActionListener(e -> {
                try {
                    GlobalScreen.unregisterNativeHook();
                } catch (NativeHookException ex) {
                    System.err.println("Failed to unregister native hook: " + ex.getMessage());
                }
                System.exit(0);
            });

            frame.add(closeButton);
            frame.setVisible(true);
        });
    }
    long last = 0;

    /**
     * This method is called whenever a native key is pressed. It handles the key presses
     * of the function keys F2, F4, F6 and F8.
     * <p>
     * F2: Records the current time.
     * <p>
     * F4: Types "ams\\{username}" then TAB, then "{password}" then TAB, then "{pin}" then END.
     * <p>
     * F6: Types "{username}" then TAB, then "{password}" then END, then does nothing.
     * <p>
     * F8: Types "{password}" then TAB, then does nothing.
     *
     * @param e The native key event
     */
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        switch (e.getKeyCode()) {
            case NativeKeyEvent.VC_F2 -> last = System.currentTimeMillis();
            case NativeKeyEvent.VC_F8 -> handleKeyPress("ams\\" + username, VK_TAB, password, VK_TAB, pin, VK_END);
            case NativeKeyEvent.VC_F9 -> handleKeyPress(username, VK_TAB, password, VK_END, null, -1);
            case NativeKeyEvent.VC_F10 -> handleKeyPress(password, VK_TAB, null, -1, null, -1);
//            case NativeKeyEvent.VC_F11 ->
//            {
//                handleKeyPress(employeeName, VK_TAB, email, VK_TAB, username, VK_TAB);
//                handleKeyPress(city, VK_TAB, null, VK_SPACE, department, VK_TAB);
//                handleKeyPress(city, VK_TAB, null, VK_SPACE, department, VK_TAB);
//            }
//            department + VK_TAB + VK_SPACE  + VK_TAB  + VK_TAB + "General meeting"  + VK_TAB + VK_SPACE, -1, null, -1, null, -1);
//            case NativeKeyEvent.VC_F10 -> handleKeyPress(username, VK_TAB, null, -1, null, -1);
        }
    }

    /**
     * Handles a key press event by typing out a phrase and pressing some keys.
     * The phrase is typed out in three parts: first, second and third.
     * The keys VK1, VK2 and VK3 are pressed between the parts of the phrase.
     * If any of the parts of the phrase or the keys are null, they are skipped.
     * The time difference between the current time and the last key press time is
     * checked. If it is less than the click span, the click span is updated and the
     * phrase is typed out.
     *
     * @param first  The first part of the phrase
     * @param VK1    The key to press after the first part of the phrase
     * @param second The second part of the phrase
     * @param VK2    The key to press after the second part of the phrase
     * @param third  The third part of the phrase
     * @param VK3    The key to press after the third part of the phrase
     */
    public void handleKeyPress(String first, int VK1, String second, int VK2, String third, int VK3) {
        long current = System.currentTimeMillis();
        if (current - last < clickSpan) {
//        clickSpan = current-last;
            System.out.println("New click span:" + (current - last));

            sleepAndTypePhrase(0, first);
            if (VK1 >= 0) sleepAndPressKey(10, VK1);
            if (second != null) sleepAndTypePhrase(20, second);
            if (VK2 >= 0) sleepAndPressKey(30, VK2);
            if (third != null) sleepAndTypePhrase(40, third);
            if (VK3 >= 0) sleepAndPressKey(50, VK3);
        }
//    last = current;

    }

    private void sleepAndPressKey(long sleep, int VK1) {
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(sleep);
//        System.out.println("Pressing " + KeyEvent.getKeyText(VK1));
                robot.keyPress(VK1);
                // Release the key; on macOS an unreleased key stays logically held down
                robot.keyRelease(VK1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void sleepAndTypePhrase(long sleep, String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(sleep);
                typePhrase(text);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void typePhrase(String text) {
//    System.out.println("Typing " + text);
        for (char c : text.toCharArray()) {
            try {
                typeIt(c);

            } catch (IllegalAccessException | NoSuchFieldException ex) {
                throw new RuntimeException(ex + " char:" + c);
            }
        }
    }

    private void typeIt(char c) throws IllegalAccessException, NoSuchFieldException {
        if (c != '!') {
            Integer keyEvent = asciiToKeyEvent.get(c);
            if (keyEvent == null) {
                keyEvent = KeyEvent.getExtendedKeyCodeForChar(c);
            }
            if (Character.isUpperCase(c)) {
                robot.keyPress(VK_SHIFT);
            }
            robot.keyPress(keyEvent);
            robot.keyRelease(keyEvent);
            robot.keyRelease(VK_SHIFT);
        } else {
            // Press and hold SHIFT
            robot.keyPress(KeyEvent.VK_SHIFT);

            // Press the '1' key
            robot.keyPress(KeyEvent.VK_1);
            robot.keyRelease(KeyEvent.VK_1);

            // Release SHIFT
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }

    static Map<Character, Integer> asciiToKeyEvent = Map.of(
            '-', KeyEvent.VK_MINUS,
            '=', KeyEvent.VK_EQUALS,
            ',', KeyEvent.VK_COMMA,
            '.', KeyEvent.VK_PERIOD,
            ';', KeyEvent.VK_SEMICOLON,
            '/', KeyEvent.VK_SLASH,
            '[', KeyEvent.VK_OPEN_BRACKET,
            '!', KeyEvent.VK_EXCLAMATION_MARK,
            '#', KeyEvent.VK_NUMBER_SIGN,
            ']', KeyEvent.VK_CLOSE_BRACKET);
}
