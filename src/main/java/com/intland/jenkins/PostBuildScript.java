/*
 * Copyright (c) 2015 Intland Software (support@intland.com)
 */

package com.intland.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.intland.jenkins.api.CodebeamerApiClient;
import com.intland.jenkins.collector.CodebeamerCollector;
import com.intland.jenkins.collector.dto.CodebeamerDto;
import com.intland.jenkins.util.PluginUtil;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostBuildScript extends Notifier {
    public static final String PLUGIN_SHORTNAME = "codebeamer-result-trend-updater";
    private String wikiUri;
    private String credentialsId;
    private Integer keepBuildNumber;

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @DataBoundConstructor
    public PostBuildScript(String wikiUri, String credentialsId, Integer keepBuildNumber) {
        this.wikiUri = wikiUri;
        this.keepBuildNumber = keepBuildNumber;
        this.credentialsId = credentialsId;
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

        StandardUsernamePasswordCredentials standardUsernamePasswordCredentials = PluginUtil.getCredentials(build.getParent(), credentialsId);

        CodebeamerApiClient apiClient = new CodebeamerApiClient(getUsername(standardUsernamePasswordCredentials), getPassword(standardUsernamePasswordCredentials), url, wikiId);

        long currentTime = System.currentTimeMillis();
        CodebeamerDto codebeamerDto = CodebeamerCollector.collectCodebeamerData(build, listener, apiClient, currentTime, keepBuildNumber);

        listener.getLogger().println("Starting wiki update");
        apiClient.updateWikiMarkup(url, wikiId, codebeamerDto.getMarkup());
        listener.getLogger().println("Wiki update finished");

        apiClient.createOrUpdateAttachment(codebeamerDto.getAttachmentName(), codebeamerDto.getAttachmentContent());
        listener.getLogger().println("Attachment uploaded");
        return true;
    }

    private String getUsername(StandardUsernamePasswordCredentials standardUsernamePasswordCredentials) {
        return standardUsernamePasswordCredentials.getUsername();
    }

    private String getPassword(StandardUsernamePasswordCredentials standardUsernamePasswordCredentials) {
        return standardUsernamePasswordCredentials.getPassword().getPlainText();
    }

    public String getWikiUri() {
        return wikiUri;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public Integer getKeepBuildNumber() {
        return keepBuildNumber;
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (project == null) {
                if (!Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!project.hasPermission(Item.EXTENDED_READ) && !project.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result
                    .includeEmptyValue()
                    .includeMatchingAs(
                            project instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task) project) : ACL.SYSTEM,
                            project,
                            StandardUsernamePasswordCredentials.class,
                            Collections.<DomainRequirement>emptyList(),
                            CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }
    }
}
