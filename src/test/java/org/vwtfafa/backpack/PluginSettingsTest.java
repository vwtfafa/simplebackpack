package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Map;
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
    void permissionSizesSelectTheLargestGrantedTier() {
        Map<String, Integer> tiers = Map.of(
                "simplebackpack.size.36", 36,
                "simplebackpack.size.54", 54);

        assertEquals(54, PluginSettings.resolveBackpackSize(27, tiers,
                permission -> permission.endsWith(".36") || permission.endsWith(".54")));
        assertEquals(36, PluginSettings.resolveBackpackSize(27, tiers,
                permission -> permission.endsWith(".36")));
        assertEquals(27, PluginSettings.resolveBackpackSize(27, tiers, permission -> false));
    }

    @Test
    void invalidPermissionSizeEntriesAreIgnored() {
        Map<String, Integer> tiers = PluginSettings.sanitizePermissionSizes(LOGGER, Map.of(
                "simplebackpack.size.54", 54,
                "simplebackpack.size.invalid", 55));

        assertEquals(Map.of("simplebackpack.size.54", 54), tiers);
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
