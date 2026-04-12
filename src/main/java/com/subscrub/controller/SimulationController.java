package com.subscrub.controller;

import com.subscrub.dto.SimRequest;
import com.subscrub.dto.SimResult;
import com.subscrub.service.SimulationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationEngine simulationEngine;

    @PostMapping("/simulate")
    public ResponseEntity<SimResult> simulate(@RequestBody SimRequest req) {
        SimResult result = simulationEngine.simulate(req.userId(), req.cancelTargets());
        return ResponseEntity.ok(result);
    }
}
