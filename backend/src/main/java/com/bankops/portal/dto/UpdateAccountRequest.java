package com.bankops.portal.dto;

import lombok.Data;

@Data
public class UpdateAccountRequest {
    private String status; // OPEN or CLOSED
    private Boolean overdraftEnabled;
}





