package com.subscrub.controller;

import com.subscrub.dto.AskRequest;
import com.subscrub.dto.AskResponse;
import com.subscrub.service.NLQOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final NLQOrchestrator orchestrator;

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest req) {
        AskResponse response = orchestrator.ask(req.query(), req.userId());
        return ResponseEntity.ok(response);
    }
}
