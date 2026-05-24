package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.NestingRequest;
import com.besenior.harucoding.DTO.NestingResponse;
import com.besenior.harucoding.service.NestingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/nesting")
@CrossOrigin(origins = "*")
public class NestingController {

    private final NestingService nestingService;

    public NestingController(NestingService nestingService) {
        this.nestingService = nestingService;
    }

    @PostMapping("/format")
    public ResponseEntity<NestingResponse> format(@RequestBody NestingRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new NestingResponse("error", null, null));
        }

        NestingResponse response = nestingService.format(request);
        return ResponseEntity.ok(response);
    }
}
