package com.hotel.shifthelper.adapter.in.web;

import com.hotel.shifthelper.application.AssignmentApplicationService;
import com.hotel.shifthelper.domain.model.AssignmentPlan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shift/reservations/{reservationId}/assignment")
class AssignmentRestController {

    private final AssignmentApplicationService service;

    AssignmentRestController(AssignmentApplicationService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    ResponseEntity<AssignmentPlan> preview(@PathVariable Long reservationId) {
        return ResponseEntity.ok(service.preview(reservationId));
    }

    @PostMapping("/commit")
    ResponseEntity<AssignmentPlan> commit(@PathVariable Long reservationId) {
        return ResponseEntity.ok(service.commit(reservationId));
    }
}
