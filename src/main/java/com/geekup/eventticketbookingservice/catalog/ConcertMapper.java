package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.operation.dto.CreateConcertRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConcertMapper {

    ConcertResponse toConcertResponse(Concert concert);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    Concert toConcert(CreateConcertRequest request);
}
