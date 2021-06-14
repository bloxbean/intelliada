package com.bloxbean.intelliada.idea.scripts.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptGenInputs {
    private Long slot;
    private Integer required;
}
