package com.nb.oddsportalfeedchecker.controller;

import com.nb.oddsportalfeedchecker.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping(path = "notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("enable")
    public ResponseEntity<String> enableNotifications() {
        return ResponseEntity.ok(notificationService.enableNotifications());
    }

    @GetMapping("disable")
    public ResponseEntity<String> disableNotifications() {
        return ResponseEntity.ok(notificationService.disableNotifications());
    }

}
