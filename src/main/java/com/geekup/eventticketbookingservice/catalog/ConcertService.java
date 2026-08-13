package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final ConcertMapper concertMapper;

    @Transactional(readOnly = true)
    public List<ConcertResponse> getPublishedConcerts() {
        return concertRepository.findByStatus(ConcertStatus.PUBLISHED)
                .stream()
                .map(concertMapper::toConcertResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConcertResponse getConcertById(Long id) {
        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONCERT_NOT_FOUND));
        return concertMapper.toConcertResponse(concert);
    }
}
