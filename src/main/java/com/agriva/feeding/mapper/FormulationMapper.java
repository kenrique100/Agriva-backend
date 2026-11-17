package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FormulationMapper {

    private final ObjectMapper objectMapper;

    public FormulationResponse toResponse(Formulation f, List<FormulationLine> lines) {
        List<FormulationResponse.Line> lineDtos = lines.stream()
                .map(l -> FormulationResponse.Line.builder()
                        .ingredientId(l.getIngredientId())
                        .ingredientName(l.getIngredientName())
                        .quantityKg(l.getQuantityKg())
                        .percentage(l.getPercentage())
                        .unitCost(l.getUnitCost())
                        .lineCost(l.getLineCost())
                        .build())
                .toList();

        return FormulationResponse.builder()
                .id(f.getId())
                .farmId(f.getFarmId())
                .name(f.getName())
                .targetSpecies(f.getTargetSpecies())
                .mode(f.getMode())
                .targetQuantityKg(f.getTargetQuantityKg())
                .targetCpValue(f.getTargetCpValue())
                .formulaTemplateId(f.getFormulaTemplateId())
                .totalKg(f.getTotalKg())
                .totalCost(f.getTotalCost())
                .algorithmVersion(f.getAlgorithmVersion())
                .nutrientSummary(parseSummary(f.getNutrientSummaryJson()))
                .createdAt(f.getCreatedAt())
                .lines(lineDtos)
                .build();
    }

    private Map<String, BigDecimal> parseSummary(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse nutrientSummaryJson: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}