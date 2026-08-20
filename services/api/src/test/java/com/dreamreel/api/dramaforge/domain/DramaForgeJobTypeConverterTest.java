package com.dreamreel.api.dramaforge.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DramaForgeJobTypeConverterTest {

    private final DramaForgeJobTypeConverter converter = new DramaForgeJobTypeConverter();

    @Test
    void mapsLegacyCoreScriptToGenerateScript() {
        assertEquals(DramaForgeJobType.GENERATE_SCRIPT, converter.convertToEntityAttribute("CORE_SCRIPT"));
        assertEquals(DramaForgeJobType.GENERATE_SCRIPT, converter.convertToEntityAttribute("core_script"));
    }

    @Test
    void mapsLegacyCoreBibleToExtractAssets() {
        assertEquals(DramaForgeJobType.EXTRACT_ASSETS, converter.convertToEntityAttribute("CORE_BIBLE"));
    }

    @Test
    void mapsKnownTypes() {
        assertEquals(DramaForgeJobType.GENERATE_SCRIPT, converter.convertToEntityAttribute("GENERATE_SCRIPT"));
        assertEquals(DramaForgeJobType.EXTRACT_ASSETS, converter.convertToEntityAttribute("EXTRACT_ASSETS"));
    }

    @Test
    void writesCanonicalEnumName() {
        assertEquals("GENERATE_SCRIPT", converter.convertToDatabaseColumn(DramaForgeJobType.GENERATE_SCRIPT));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("NOT_A_REAL_JOB"));
    }
}
