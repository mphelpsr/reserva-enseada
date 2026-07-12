package com.empresa.vesselmanagement.api.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.empresa.vesselmanagement.domain.availability.TourType;

/**
 * O conversor padrão do Spring para @PathVariable enum usa Enum.valueOf (nomes em
 * MAIÚSCULO, ex. "ALTO_MAR"), mas as rotas usam os valores em minúsculo do FR-012
 * ("alto_mar"/"orla"). Sem este converter, `/vessels/{id}/availability/{data}/alto_mar`
 * falharia mesmo com um valor válido.
 */
@Component
public class TourTypeConverter implements Converter<String, TourType> {

    @Override
    public TourType convert(@NonNull String source) {
        return TourType.fromValue(source);
    }
}
