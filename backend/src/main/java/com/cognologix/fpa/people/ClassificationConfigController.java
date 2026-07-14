package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ClassificationConfigType;
import com.cognologix.fpa.people.dto.ClassificationConfigResponse;
import com.cognologix.fpa.people.dto.CreateClassificationConfigRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/people/config")
@RequiredArgsConstructor
@Tag(name = "People — Classification Config", description = "Delivery PU / Management BU / Leadership BU lists")
public class ClassificationConfigController {

    private final PeoplePayrollService peoplePayrollService;

    @GetMapping("/classification")
    @Operation(summary = "List all classification config entries grouped by config_type")
    public Map<ClassificationConfigType, List<ClassificationConfigResponse>> list() {
        return peoplePayrollService.findAllClassificationConfig().stream()
                .map(ClassificationConfigResponse::from)
                .collect(Collectors.groupingBy(
                        ClassificationConfigResponse::configType,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @PostMapping("/classification")
    @Operation(summary = "Add a value to a config_type. Returns 409 if duplicate.")
    public ResponseEntity<ClassificationConfigResponse> add(
            @Valid @RequestBody CreateClassificationConfigRequest req) {
        var saved = peoplePayrollService.addClassificationConfig(req.configType(), req.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassificationConfigResponse.from(saved));
    }

    @DeleteMapping("/classification/{id}")
    @Operation(summary = "Remove a classification config entry. Fails if it would leave the type empty.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        peoplePayrollService.deleteClassificationConfig(id);
        return ResponseEntity.noContent().build();
    }
}
