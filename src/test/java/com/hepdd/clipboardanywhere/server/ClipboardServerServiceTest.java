package com.hepdd.clipboardanywhere.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClipboardServerServiceTest {

    @Test
    public void sanitizesFormattingControlCharactersAndLength() {
        String longName = "\u00a7aGreen\nName 1234567890123456789012345678901234567890";
        String result = ClipboardServerService.sanitizeName(longName);

        assertEquals("GreenName 1234567890123456789012", result);
        assertEquals(32, result.length());
    }
}
