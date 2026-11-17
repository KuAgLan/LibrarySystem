package com.library.controller;

import com.library.common.R;
import com.library.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public R<Map<String, String>> get() {
        return R.ok(settingService.all());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<Map<String, String>> save(@RequestBody Map<String, String> values) {
        return R.ok(settingService.save(values));
    }
}
