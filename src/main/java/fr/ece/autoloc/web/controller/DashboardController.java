package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.DashboardService;
import fr.ece.autoloc.web.dto.DashboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardDto dashboard() {
        return dashboardService.resume();
    }
}
