package com.hepdd.clipboardanywhere.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.settings.KeyBinding;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

import com.hepdd.clipboardanywhere.client.ClientEventHandler;
import com.hepdd.clipboardanywhere.client.KeyBindingDisplay;

import jds.bibliocraft.gui.GuiBiblioTextField;

public class ClientFocusAndSafetyTest {

    @Test
    public void keyBindingDisplayOuterClassHasZeroDirectMkbConstantPoolReference() throws IOException {
        try (InputStream stream = KeyBindingDisplay.class.getResourceAsStream("KeyBindingDisplay.class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int read;
            while ((read = stream.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            String ascii = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
            assertFalse(
                "Outer KeyBindingDisplay.class must not reference committee/nova/mkb directly",
                ascii.contains("committee/nova/mkb/api/IKeyBinding"));
        }

        assertEquals("NONE", KeyBindingDisplay.getDisplayString(null));
        assertFalse(KeyBindingDisplay.isActiveAndMatches(null, Keyboard.KEY_E));
        KeyBinding unbound = new KeyBinding("test.unbound", Keyboard.KEY_NONE, "test.cat");
        assertEquals("NONE", KeyBindingDisplay.getDisplayString(unbound));
        assertFalse(KeyBindingDisplay.isActiveAndMatches(unbound, Keyboard.KEY_E));
    }

    @Test
    public void cachesSingleAndArrayTextFieldReflectionsAcrossHierarchy() throws Exception {
        Field[] firstLookup = invokeGetCachedFields(DummyArrayBookScreen.class);
        Field[] secondLookup = invokeGetCachedFields(DummyArrayBookScreen.class);
        assertSame("Reflected Field[] must be cached per screen Class", firstLookup, secondLookup);
        assertEquals(3, firstLookup.length);

        DummyArrayBookScreen screen = new DummyArrayBookScreen();
        assertFalse(invokeHasReflectedFocus(screen));

        GuiBiblioTextField biblioField = allocateWithoutConstructor(GuiBiblioTextField.class);
        biblioField.setFocused(true);
        screen.biblioLines = new GuiBiblioTextField[] { null, biblioField };
        assertTrue("Focused element inside GuiBiblioTextField[] must be detected", invokeHasReflectedFocus(screen));

        biblioField.setFocused(false);
        assertFalse(invokeHasReflectedFocus(screen));

        GuiTextField vanillaField = allocateWithoutConstructor(GuiTextField.class);
        setPrivateBoolean(GuiTextField.class, vanillaField, "isFocused", true);
        screen.vanillaArray = new GuiTextField[] { vanillaField };
        assertTrue("Focused element inside GuiTextField[] must be detected", invokeHasReflectedFocus(screen));
    }

    @Test
    public void preventsSilentTruncationWhenLongTaskIsOpenedAndUnmodified() {
        String longOriginal = "This task has way more than twenty-three characters in BiblioCraft";
        String truncatedByTextField = longOriginal.substring(0, 23);

        assertFalse(
            "Unmodified focus loss must NOT submit truncated 23-char text",
            ClipboardOverlay.shouldSubmitTaskEdit(longOriginal, truncatedByTextField, false));
        assertFalse(
            "Modified flag with identical resulting text need not send redundant packet",
            ClipboardOverlay.shouldSubmitTaskEdit("Short task", "Short task", true));
        assertTrue(
            "Explicit user edit changing text must submit",
            ClipboardOverlay.shouldSubmitTaskEdit(longOriginal, "Shortened intentionally", true));
    }

    private static Field[] invokeGetCachedFields(Class<?> clazz) throws Exception {
        java.lang.reflect.Method m = ClientEventHandler.class.getDeclaredMethod("getCachedTextFields", Class.class);
        m.setAccessible(true);
        return (Field[]) m.invoke(null, clazz);
    }

    private static boolean invokeHasReflectedFocus(Object screen) throws Exception {
        java.lang.reflect.Method m = ClientEventHandler.class
            .getDeclaredMethod("hasReflectedTextFieldFocus", Object.class);
        m.setAccessible(true);
        return ((Boolean) m.invoke(null, screen)).booleanValue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return (T) unsafe.allocateInstance(type);
    }

    private static void setPrivateBoolean(Class<?> owner, Object target, String name, boolean value) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.setBoolean(target, value);
    }

    private static class BaseDummyScreen {

        @SuppressWarnings("unused")
        private GuiTextField searchBox;
    }

    private static final class DummyArrayBookScreen extends BaseDummyScreen {

        private GuiBiblioTextField[] biblioLines;
        private GuiTextField[] vanillaArray;
        @SuppressWarnings("unused")
        private String ignoredNonTextField = "noop";
    }
}
