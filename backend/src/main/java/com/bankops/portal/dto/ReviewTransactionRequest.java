package com.bankops.portal.dto;

import lombok.Data;

@Data
public class ReviewTransactionRequest {
    private String actorId;
    private String notes;
}
