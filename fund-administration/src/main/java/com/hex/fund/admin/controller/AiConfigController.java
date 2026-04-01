package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.service.ai.AiModelService;
import com.hex.fund.service.entity.AgentModelBinding;
import com.hex.fund.service.entity.AiProviderConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 提供商与 Agent 模型绑定配置 API。
 */
@Tag(name = "AI Config", description = "AI provider and model binding management")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiModelService aiModelService;

    @Operation(summary = "List all AI providers")
    @GetMapping("/providers")
    public ApiResponse<List<AiProviderConfig>> listProviders() {
        return ApiResponse.ok(aiModelService.listAllProviders());
    }

    @Operation(summary = "Save or update AI provider config")
    @PostMapping("/providers")
    public ApiResponse<Void> saveProvider(@RequestBody AiProviderConfig config) {
        aiModelService.saveProvider(config);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Test provider connectivity")
    @PostMapping("/test/{providerCode}")
    public ApiResponse<String> testConnectivity(@PathVariable String providerCode) {
        try {
            String response = aiModelService.testConnectivity(providerCode);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            return ApiResponse.fail(500, "Connectivity test failed: " + e.getMessage());
        }
    }

    @Operation(summary = "List all agent model bindings")
    @GetMapping("/bindings")
    public ApiResponse<List<AgentModelBinding>> listBindings() {
        return ApiResponse.ok(aiModelService.listBindings());
    }

    @Operation(summary = "Save or update agent model binding")
    @PostMapping("/bindings")
    public ApiResponse<Void> saveBinding(@RequestBody AgentModelBinding binding) {
        aiModelService.saveBinding(binding);
        return ApiResponse.ok(null);
    }
}
