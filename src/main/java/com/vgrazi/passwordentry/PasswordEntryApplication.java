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

import static java.awt.event.KeyEvent.*;

@SpringBootApplication
public class PasswordEntryApplication implements NativeKeyListener {

  @Value("${name}")
  private String username;

  @Value("${pwd}")
  private String password;

  @Value("${pin}")
  private String pin;

  @Value("${click-span}")
  private long clickSpan;

  private final Robot robot;

  public PasswordEntryApplication() throws AWTException {
    robot = new Robot();
  }

  public static void main(String[] args) {
    SpringApplicationBuilder builder = new SpringApplicationBuilder(PasswordEntryApplication.class);
    builder.headless(false).run(args);
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return args -> {
      // Register JNativeHook
      register();
      // Disable JNativeHook logging
      java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
      logger.setLevel(java.util.logging.Level.OFF);
      logger.setUseParentHandlers(false);
    };
  }

  volatile boolean registered;

  private synchronized void register() {
    if(!registered) {
      try {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
      } catch(NativeHookException ex) {
        throw new RuntimeException(ex);
      }
      registered = true;
    }
  }

  long last = 0;
  @Override
  public void nativeKeyPressed(NativeKeyEvent e) {
    if(e.getKeyCode()==NativeKeyEvent.VC_F8) {
      long current = System.currentTimeMillis();
      if(current - last < clickSpan) {
//        clickSpan = current-last;
        System.out.println("New click span:"+(current-last));
        SwingUtilities.invokeLater(() -> {
          typePhrase(username);
          robot.keyPress(VK_TAB);
        });

        SwingUtilities.invokeLater(() -> typePhrase(password));
        SwingUtilities.invokeLater(() -> robot.keyPress(VK_END));
      }
      last = current;
    }
    if(e.getKeyCode()==NativeKeyEvent.VC_F4) {
      long current = System.currentTimeMillis();
      if(current - last < clickSpan) {
//        clickSpan = current-last;
        System.out.println("New click span:"+(current-last));
        SwingUtilities.invokeLater(() -> {
          typePhrase("ams\\"+username);
          robot.keyPress(VK_TAB);
        });

        SwingUtilities.invokeLater(() -> {
          typePhrase(password);
          robot.keyPress(VK_TAB);
        });
        SwingUtilities.invokeLater(() -> typePhrase(pin));
        SwingUtilities.invokeLater(() -> robot.keyPress(VK_END));

      }
      last = current;
    }
  }

  private void typePhrase(String pwd) {
    for(char c : pwd.toCharArray()) {
      try {
        typeIt(c);

      } catch(IllegalAccessException | NoSuchFieldException ex) {
        throw new RuntimeException(ex + " char:" + c);
      }
    }
  }

  private void typeIt(char c) throws IllegalAccessException, NoSuchFieldException {
    if(c=='!') {
      // Press and hold SHIFT
      robot.keyPress(KeyEvent.VK_SHIFT);

      // Press the '1' key
      robot.keyPress(KeyEvent.VK_1);
      robot.keyRelease(KeyEvent.VK_1);

      // Release SHIFT
      robot.keyRelease(KeyEvent.VK_SHIFT);
    } else {
      Integer keyEvent = asciiToKeyEvent.get(c);
      if(keyEvent==null) {
        keyEvent = KeyEvent.getExtendedKeyCodeForChar(c);
      }
      if(Character.isUpperCase(c)) {
        robot.keyPress(VK_SHIFT);
      }
      robot.keyPress(keyEvent);
      robot.keyRelease(keyEvent);
      robot.keyRelease(VK_SHIFT);
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
