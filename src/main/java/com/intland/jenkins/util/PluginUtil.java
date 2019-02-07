/*
 * Copyright (c) 2015 Intland Software (support@intland.com)
 */

package com.intland.jenkins.util;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Item;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.util.Collections;

public class PluginUtil {
    public static boolean isPerformancePluginInstalled() {
        return isPluginInstalled("performance");
    }

    public static boolean isGitPluginInstalled() {
        return isPluginInstalled("git");
    }

    public static boolean isMercurialPluginInstalled() {
        return isPluginInstalled("mercurial");
    }

    private static boolean isPluginInstalled(String pluginName) {
        return Jenkins.getInstance().getPlugin(pluginName) != null;
    }

    public static StandardUsernamePasswordCredentials getCredentials(Item job, String credentialsId) {
        StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        job,
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
        );
        return credentials;
    }
}
