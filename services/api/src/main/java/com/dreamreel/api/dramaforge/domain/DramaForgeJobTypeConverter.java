package com.dreamreel.api.dramaforge.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;
import java.util.Map;

/**
 * Maps DramaForge job type strings from the DB, including legacy aliases
 * that were renamed in application code.
 */
@Converter(autoApply = false)
public class DramaForgeJobTypeConverter implements AttributeConverter<DramaForgeJobType, String> {

    /** Old DB values → current enum constants. */
    private static final Map<String, DramaForgeJobType> LEGACY_ALIASES = Map.of(
            "CORE_SCRIPT", DramaForgeJobType.GENERATE_SCRIPT,
            "CORE_BIBLE", DramaForgeJobType.EXTRACT_ASSETS
    );

    @Override
    public String convertToDatabaseColumn(DramaForgeJobType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public DramaForgeJobType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        var normalized = dbData.trim().toUpperCase(Locale.ROOT);
        var legacy = LEGACY_ALIASES.get(normalized);
        if (legacy != null) {
            return legacy;
        }
        return DramaForgeJobType.valueOf(normalized);
    }
}
