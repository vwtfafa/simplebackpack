package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void nullAndEmptyDeserializeToEmpty() {
        assertEquals(Component.empty(), Messages.deserialize(null));
        assertEquals(Component.empty(), Messages.deserialize(""));
    }

    @Test
    void validMiniMessageKeepsFormatting() {
        Component component = Messages.deserialize("<red>hi</red>");
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void legacyColorsStillWork() {
        assertDoesNotThrow(() -> Messages.deserialize("§aHi"));
    }

    @Test
    void brokenMiniMessageFallsBackToPlainText() {
        assertEquals(Component.text("<red"), Messages.deserialize("<red"));
        assertEquals(Component.text("<#notacolor>"), Messages.deserialize("<#notacolor>"));
    }

    @Test
    void placeholdersSubstituteNormalValues() {
        assertEquals(Component.text("Hi Notch!"),
                Messages.renderTemplate("Hi {player}!", "{player}", "Notch"));
    }

    @Test
    void placeholderValuesCannotInjectFormatting() {
        assertEquals(Component.text("Hi <red>Notch!"),
                Messages.renderTemplate("Hi {player}!", "{player}", "<red>Notch"));
    }

    @Test
    void templateFormattingStillApplies() {
        Component rendered = Messages.renderTemplate("<green>Hi {player}!", "{player}", "Notch");
        assertEquals(NamedTextColor.GREEN, rendered.color());
    }

    @Test
    void legacyTemplatesKeepStringSubstitution() {
        assertDoesNotThrow(() -> Messages.renderTemplate("§aHi {player}!", "{player}", "<red>Notch"));
    }
}
