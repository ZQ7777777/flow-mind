package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowStartContextResponse;
import com.flowmind.business.workflow.dto.WorkflowStartSubmitRequest;
import com.flowmind.business.workflow.dto.WorkflowStartSubmitResponse;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用流程发起上下文与提交接口。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/api/workflow/processes/{processCode}")
public class WorkflowStartController {

    private final WorkflowStartService service;

    public WorkflowStartController(WorkflowStartService service) {
        this.service = service;
    }

    @GetMapping("/start-context")
    public WorkflowStartContextResponse startContext(@PathVariable String processCode) {
        return service.startContext(processCode);
    }

    @PostMapping(value = "/start-submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkflowStartSubmitResponse startAndSubmit(
            @PathVariable String processCode,
            @RequestPart("payload") WorkflowStartSubmitRequest payload,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            MultipartHttpServletRequest multipartRequest) {
        MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<String, MultipartFile>();
        for (Map.Entry<String, List<MultipartFile>> entry : multipartRequest.getMultiFileMap().entrySet()) {
            if (!"payload".equals(entry.getKey())) {
                files.put(entry.getKey(), new ArrayList<MultipartFile>(entry.getValue()));
            }
        }
        return service.startAndSubmit(processCode, payload, files, idempotencyKey);
    }
}
