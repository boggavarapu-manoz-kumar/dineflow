package com.dineflow.menu.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.menu.dto.CustomerMenuResponse;
import com.dineflow.menu.service.CustomerMenuService;
import com.dineflow.security.DineFlowPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/menu")
public class CustomerMenuController {

    private final CustomerMenuService customerMenuService;

    public CustomerMenuController(CustomerMenuService customerMenuService) {
        this.customerMenuService = customerMenuService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('MENU_VIEW')")
    public ResponseEntity<ApiResponse<CustomerMenuResponse>> getCustomerMenu(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        CustomerMenuResponse response = customerMenuService.getMenuForGuest(principal);
        return ResponseEntity.ok(ApiResponse.ok(response, "Customer menu retrieved successfully"));
    }
}
