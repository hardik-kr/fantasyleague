package com.cricket.fantasyleague.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;
import com.cricket.fantasyleague.service.workflow.TransferWorkflowService;

@RestController
@RequestMapping("/season")
public class UserTransferController
{
    private final TransferWorkflowService transferWorkflowService;

    public UserTransferController(TransferWorkflowService transferWorkflowService) {
        this.transferWorkflowService = transferWorkflowService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse> transferMethod(@RequestBody UserTransferDto userTransferDto)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDto userobj = (UserDto) authentication.getPrincipal();

        transferWorkflowService.makeTransferForCurrentWindow(userTransferDto, userobj.getEmail());
        return successResponse();
    }

    private ResponseEntity<ApiResponse> successResponse()
    {
        String msg = String.format("success");
        ApiResponse response = new ApiResponse(msg, true, HttpStatus.CREATED.value(), HttpStatus.CREATED);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
