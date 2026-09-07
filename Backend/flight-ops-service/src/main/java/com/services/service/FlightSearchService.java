package com.services.service;

import com.common.dto.FareDto;
import com.services.client.FareClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.dto.FlightSearchResultDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.entity.FlightInstanceStatus;
import com.services.mapper.FlightMapper;
import com.services.repository.FlightInstanceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FlightSearchService {

    private final FlightInstanceRepository flightInstanceRepository;
    private final FareClient fareClient;
    private final FlightService flightService;

    public FlightSearchService(FlightInstanceRepository flightInstanceRepository, FareClient fareClient,
                                FlightService flightService) {
        this.flightInstanceRepository = flightInstanceRepository;
        this.fareClient = fareClient;
        this.flightService = flightService;
    }

    /**
     * Route + calendar-date search across SCHEDULED flight instances, optionally filtered by cabin
     * class and/or price range. Fares are fetched with exactly one bulk Feign call regardless of
     * how many flights matched, same N+1-avoidance discipline as everywhere else in this codebase.
     *
     * <p>A flight instance with no fare matching an active price/cabin-class filter is excluded
     * entirely (we can't confirm it belongs in filtered results without fare data). With no such
     * filter active, every matching instance is still returned - with {@code fare: null} if it has
     * no fares set up yet, so browsing isn't blocked by incomplete pricing data.
     */
    public List<FlightSearchResultDto> searchFlights(Long departureAirportId, Long arrivalAirportId, LocalDate date,
                                                       String cabinClass, BigDecimal minPrice, BigDecimal maxPrice,
                                                       String sortBy) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<FlightInstance> instances = flightInstanceRepository
                .findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                        departureAirportId, arrivalAirportId, startOfDay, endOfDay, FlightInstanceStatus.SCHEDULED);

        if (instances.isEmpty()) {
            return List.of();
        }

        List<Flight> distinctFlights = instances.stream()
                .collect(Collectors.toMap(i -> i.getFlight().getId(), FlightInstance::getFlight, (a, b) -> a))
                .values()
                .stream()
                .toList();
        Map<Long, FlightDto> flightDtoById = flightService.enrichFlights(distinctFlights).stream()
                .collect(Collectors.toMap(FlightDto::getId, Function.identity()));

        List<Long> flightIds = distinctFlights.stream().map(Flight::getId).toList();
        Map<Long, List<FareDto>> faresByFlightId = fareClient.getFaresByFlightIds(flightIds).stream()
                .collect(Collectors.groupingBy(FareDto::getFlightId));

        boolean priceFilterActive = cabinClass != null || minPrice != null || maxPrice != null;

        List<FlightSearchResultDto> results = new ArrayList<>();
        for (FlightInstance instance : instances) {
            Long flightId = instance.getFlight().getId();
            FlightInstanceDto instanceDto = FlightMapper.toDto(instance, flightDtoById.get(flightId), null);

            List<FareDto> matchingFares = faresByFlightId.getOrDefault(flightId, List.of()).stream()
                    .filter(fare -> cabinClass == null || cabinClass.equalsIgnoreCase(fare.getCabinClass()))
                    .filter(fare -> minPrice == null || fare.getPrice().compareTo(minPrice) >= 0)
                    .filter(fare -> maxPrice == null || fare.getPrice().compareTo(maxPrice) <= 0)
                    .toList();

            if (!matchingFares.isEmpty()) {
                matchingFares.forEach(fare -> results.add(new FlightSearchResultDto(instanceDto, fare)));
            } else if (!priceFilterActive) {
                results.add(new FlightSearchResultDto(instanceDto, null));
            }
        }

        return sortResults(results, sortBy);
    }

    private List<FlightSearchResultDto> sortResults(List<FlightSearchResultDto> results, String sortBy) {
        Comparator<FlightSearchResultDto> comparator = "price".equalsIgnoreCase(sortBy)
                ? Comparator.comparing(r -> r.getFare() != null ? r.getFare().getPrice() : BigDecimal.valueOf(Long.MAX_VALUE))
                : Comparator.comparing(r -> r.getFlightInstance().getDepartureTime());
        return results.stream().sorted(comparator).toList();
    }
}
