package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final ConcertMapper concertMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "concerts", key = "#pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort")
    public Page<ConcertResponse> getPublishedConcerts(Pageable pageable) {
        return concertRepository.findByStatus(ConcertStatus.PUBLISHED, pageable)
                .map(concertMapper::toConcertResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "concert", key = "#id")
    public ConcertResponse getConcertById(Long id) {
        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONCERT_NOT_FOUND));
        return concertMapper.toConcertResponse(concert);
    }

    @CacheEvict(value = {"concerts", "concert"}, allEntries = true)
    public void evictConcertCache() {
        // Evicts all cached concerts when concert status or details change
    }
}
