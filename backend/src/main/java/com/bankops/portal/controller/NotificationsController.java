package com.bankops.portal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bankops.portal.dto.NotificationsSummaryDto;
import com.bankops.portal.service.NotificationsService;

import lombok.RequiredArgsConstructor;

/** Read-only ops notifications feed (derived on read). */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationsService notificationsService;

    @GetMapping
    public NotificationsSummaryDto getNotifications() {
        return notificationsService.getNotifications();
    }
}
