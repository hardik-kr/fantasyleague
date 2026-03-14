package com.cricket.fantasyleague.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.service.workflow.TestWorkflowService;

@RestController
@RequestMapping("/test")
public class TestController
{
    private final TestWorkflowService testWorkflowService;

    public TestController(TestWorkflowService testWorkflowService) {
        this.testWorkflowService = testWorkflowService;
    }

    @PostMapping("/points/{id}")
    public ResponseEntity<ApiResponse> testScore(@PathVariable Integer id)
    {
        testWorkflowService.calculateTestPoints(id);
        String msg = String.format("success",id) ;
        ApiResponse response = new ApiResponse(msg,true,HttpStatus.CREATED.value(),HttpStatus.CREATED) ;
        return new ResponseEntity<>(response, HttpStatus.CREATED) ;
    }
}
