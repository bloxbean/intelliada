package com.bloxbean.intelliada.idea.aiken.module.pkg;

import lombok.Data;

import java.util.List;

@Data
public class AikenToml {
    private String name;
    private String version;
    private String license;
    private String description;

    private Repository repository;
    private List<Dependency> dependencies;
}

