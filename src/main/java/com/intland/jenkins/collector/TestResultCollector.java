/*
 * Copyright (c) 2015 Intland Software (support@intland.com)
 */

package com.intland.jenkins.collector;

import com.intland.jenkins.util.TimeUtil;
import com.intland.jenkins.collector.dto.TestResultDto;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.TestResult;

import java.util.List;

public class TestResultCollector {
    public static TestResultDto collectTestResultData(AbstractBuild<?, ?> build, BuildListener listener) {
        String formattedTestDuration = "";
        int totalCount = 0;
        int failCount = 0;
        int lastFailCount = 0;
        String failedDifference = "";
        long testDuration = 0l;

        AbstractTestResultAction action = build.getAction(AbstractTestResultAction.class);
        Object lastTestResult = getPreviousTestResult(build);
        if (action != null && action.getResult() != null) {
            if (action.getResult() instanceof List) { // aggregateResult
                List<AggregatedTestResultAction.ChildReport> childReports = (List<AggregatedTestResultAction.ChildReport>) action.getResult();
                for (AggregatedTestResultAction.ChildReport childReport : childReports) {
                    TestResult testResult = (TestResult) childReport.result;
                    testDuration += new Float(testResult.getDuration() * 1000).longValue();
                    totalCount += testResult.getTotalCount();
                    failCount += testResult.getFailCount();
                }

                if (lastTestResult != null) {
                    childReports = (List<AggregatedTestResultAction.ChildReport>) lastTestResult;
                    for (AggregatedTestResultAction.ChildReport childReport : childReports) {
                        TestResult testResult = (TestResult) childReport.result;
                        lastFailCount += testResult.getFailCount();
                    }
                }

                formattedTestDuration = TimeUtil.formatMillisIntoMinutesAndSeconds(testDuration);
            } else if (action.getResult() instanceof TestResult)  { // junit result
                TestResult testResult = (TestResult) action.getResult();
                testDuration = new Float(testResult.getDuration() * 1000).longValue();
                formattedTestDuration = TimeUtil.formatMillisIntoMinutesAndSeconds(testDuration);
                totalCount = testResult.getTotalCount();
                failCount = testResult.getFailCount();

                if (lastTestResult != null) {
                    lastFailCount = ((TestResult) lastTestResult).getFailCount();
                }
            } else {
                listener.getLogger().println("This build does not have a supported test run type");
            }

            failedDifference = failDifference(failCount, lastFailCount);
        } else {
            listener.getLogger().println("This build does not have a test run");
        }

        return new TestResultDto(formattedTestDuration, totalCount, failCount, failedDifference, testDuration);
    }

    private static Object getPreviousTestResult(AbstractBuild build) {
        AbstractTestResultAction result = null;

        int counter = build.getNumber();
        while (result == null && counter > 0) {
            counter--;
            Run candidateBuild = build.getParent().getBuild(String.valueOf(counter));

            if (candidateBuild == null) {
                continue;
            }

            AbstractTestResultAction candidateTestResultAction = candidateBuild.getAction(AbstractTestResultAction.class);
            if (candidateTestResultAction != null) {
                return candidateTestResultAction.getResult();
            }
        }
        return null;
    }

    private static String failDifference(int failCount1, int failCount2) {
        String sign;
        if (failCount1 > failCount2) {
            sign = "+";
        } else if (failCount1 < failCount2) {
            sign = "-";
        } else {
            sign = "±";
        }

        return sign + Math.abs(failCount1 - failCount2);
    }
}
