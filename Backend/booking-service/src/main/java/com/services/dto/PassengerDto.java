package com.services.dto;

import com.services.entity.Gender;
import com.services.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerDto {
    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String passportNumber;
    private String nationality;
    private Long seatInstanceId;
    private String ticketNumber;
    private TicketStatus ticketStatus;
}
