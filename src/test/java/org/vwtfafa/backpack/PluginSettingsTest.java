package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

class PluginSettingsTest {
    private static final Logger LOGGER = Logger.getLogger("PluginSettingsTest");

    @Test
    void validSizesPassThrough() {
        assertEquals(9, PluginSettings.sanitizeBackpackSize(LOGGER, 9));
        assertEquals(27, PluginSettings.sanitizeBackpackSize(LOGGER, 27));
        assertEquals(54, PluginSettings.sanitizeBackpackSize(LOGGER, 54));
    }

    @Test
    void invalidSizesFallBackToDefault() {
        assertEquals(27, PluginSettings.sanitizeBackpackSize(LOGGER, 0));
        assertEquals(27, PluginSettings.sanitizeBackpackSize(LOGGER, 7));
        assertEquals(27, PluginSettings.sanitizeBackpackSize(LOGGER, 28));
        assertEquals(27, PluginSettings.sanitizeBackpackSize(LOGGER, 100));
        assertEquals(27, PluginSettings.sanitizeBackpackSize(LOGGER, -9));
    }

    @Test
    void validNamesPassThrough() {
        assertEquals("<aqua>Simple Backpack",
                PluginSettings.sanitizeBackpackName(LOGGER, "<aqua>Simple Backpack"));
    }

    @Test
    void invalidNamesFallBackToDefault() {
        assertEquals("<aqua>Simple Backpack", PluginSettings.sanitizeBackpackName(LOGGER, null));
        assertEquals("<aqua>Simple Backpack", PluginSettings.sanitizeBackpackName(LOGGER, ""));
        assertEquals("<aqua>Simple Backpack", PluginSettings.sanitizeBackpackName(LOGGER, "   "));
    }
}
