package com.services.mapper;

import com.common.dto.BaggagePolicyDto;
import com.common.dto.FareDto;
import com.common.dto.FareRulesDto;
import com.services.entity.BaggagePolicy;
import com.services.entity.Fare;
import com.services.entity.FareRules;

public class FareMapper {

    public static FareDto toDto(Fare fare) {
        FareDto dto = new FareDto();
        dto.setId(fare.getId());
        dto.setFlightId(fare.getFlightId());
        dto.setCabinClass(fare.getCabinClass());
        dto.setPrice(fare.getPrice());
        dto.setCurrency(fare.getCurrency());
        dto.setFareRules(fare.getFareRules() != null ? toDto(fare.getFareRules()) : null);
        dto.setBaggagePolicy(fare.getBaggagePolicy() != null ? toDto(fare.getBaggagePolicy()) : null);
        return dto;
    }

    public static Fare toEntity(FareDto dto) {
        Fare fare = new Fare();
        fare.setId(dto.getId());
        fare.setFlightId(dto.getFlightId());
        fare.setCabinClass(dto.getCabinClass());
        fare.setPrice(dto.getPrice());
        fare.setCurrency(dto.getCurrency());
        return fare;
    }

    public static FareRulesDto toDto(FareRules fareRules) {
        FareRulesDto dto = new FareRulesDto();
        dto.setId(fareRules.getId());
        dto.setRefundable(fareRules.isRefundable());
        dto.setChangeable(fareRules.isChangeable());
        dto.setCancellationFee(fareRules.getCancellationFee());
        dto.setChangeFee(fareRules.getChangeFee());
        dto.setRefundDeadlineHours(fareRules.getRefundDeadlineHours());
        dto.setChangeDeadlineHours(fareRules.getChangeDeadlineHours());
        return dto;
    }

    public static FareRules toEntity(FareRulesDto dto) {
        FareRules fareRules = new FareRules();
        fareRules.setRefundable(dto.isRefundable());
        fareRules.setChangeable(dto.isChangeable());
        fareRules.setCancellationFee(dto.getCancellationFee());
        fareRules.setChangeFee(dto.getChangeFee());
        fareRules.setRefundDeadlineHours(dto.getRefundDeadlineHours());
        fareRules.setChangeDeadlineHours(dto.getChangeDeadlineHours());
        return fareRules;
    }

    public static BaggagePolicyDto toDto(BaggagePolicy baggagePolicy) {
        BaggagePolicyDto dto = new BaggagePolicyDto();
        dto.setId(baggagePolicy.getId());
        dto.setCabinBaggageAllowanceKg(baggagePolicy.getCabinBaggageAllowanceKg());
        dto.setCheckedBaggageAllowanceKg(baggagePolicy.getCheckedBaggageAllowanceKg());
        dto.setCheckedBaggagePieces(baggagePolicy.getCheckedBaggagePieces());
        dto.setExtraBaggageFeePerKg(baggagePolicy.getExtraBaggageFeePerKg());
        return dto;
    }

    public static BaggagePolicy toEntity(BaggagePolicyDto dto) {
        BaggagePolicy baggagePolicy = new BaggagePolicy();
        baggagePolicy.setCabinBaggageAllowanceKg(dto.getCabinBaggageAllowanceKg());
        baggagePolicy.setCheckedBaggageAllowanceKg(dto.getCheckedBaggageAllowanceKg());
        baggagePolicy.setCheckedBaggagePieces(dto.getCheckedBaggagePieces());
        baggagePolicy.setExtraBaggageFeePerKg(dto.getExtraBaggageFeePerKg());
        return baggagePolicy;
    }
}
