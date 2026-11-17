package com.library.controller;

import com.library.common.R;
import com.library.dto.ReservationRequest;
import com.library.entity.Reservation;
import com.library.service.ReservationService;
import com.library.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public R<List<ReservationVO>> list(@RequestParam(required = false) String status) {
        return R.ok(reservationService.list(status));
    }

    @PostMapping
    public R<Reservation> create(@Valid @RequestBody ReservationRequest request) {
        return R.ok(reservationService.create(request));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return R.success();
    }
}
