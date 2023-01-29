package com.bloxbean.intelliada.idea.aiken.module.pkg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Repository {
    private String user;
    private String project;
    private String platform;
}
