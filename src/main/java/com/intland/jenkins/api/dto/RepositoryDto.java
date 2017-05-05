/*
 * Copyright (c) 2017 Intland Software (support@intland.com)
 *
 * Additional information can be found here: https://codebeamer.com/cb/project/1025
 * If you find any bugs please use the Tracker page to report them: https://codebeamer.com/cb/project/1025/tracker
 */
package com.intland.jenkins.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author mgansler
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryDto {
    String uri;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
