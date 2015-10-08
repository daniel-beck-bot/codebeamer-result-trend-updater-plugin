/*
 * Copyright (c) 2015 Intland Software (support@intland.com)
 */

package com.intland.jenkins;

import com.intland.jenkins.api.CodebeamerApiClient;
import com.intland.jenkins.collector.*;
import com.intland.jenkins.collector.dto.*;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostBuildScript extends Notifier {
    public static final String PLUGIN_SHORTNAME = "codebeamer-result-trend-updater";
    private String wikiUri;
    private String username;
    private String password;

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @DataBoundConstructor
    public PostBuildScript(String wikiUri, String username, String password) {
        this.wikiUri = wikiUri;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        Pattern wikiUrlPattern = Pattern.compile("(https?://.+)/wiki/(\\d+)");
        Matcher wikiUrlMatcher = wikiUrlPattern.matcher(wikiUri);
        if (!wikiUrlMatcher.find()) {
            listener.getLogger().println("Invalid Codebeamer URI, skipping....");
            return true;
        }

        String url = wikiUrlMatcher.group(1);
        String wikiId = wikiUrlMatcher.group(2);

        CodebeamerApiClient apiClient = new CodebeamerApiClient(username, password, url, wikiId);

        long currentTime = System.currentTimeMillis();
        CodebeamerDto codebeamerDto = CodebeamerCollector.collectCodebeamerData(build, listener, apiClient, currentTime);

        listener.getLogger().println("Starting wiki update");
        apiClient.updateWikiMarkup(url, wikiId, codebeamerDto.getMarkup());
        listener.getLogger().println("Wiki update finished");

        apiClient.createOrUpdateAttachment(codebeamerDto.getAttachmentName(), codebeamerDto.getAttachmentContent());
        listener.getLogger().println("Attachment uploaded");
        return true;
    }

    public String getWikiUri() {
        return wikiUri;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }


    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Codebeamer Test Results Trend Updater";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/" + PLUGIN_SHORTNAME + "/help/help.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
