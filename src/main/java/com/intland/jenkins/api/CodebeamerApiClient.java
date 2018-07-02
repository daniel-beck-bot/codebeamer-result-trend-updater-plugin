/*
 * Copyright (c) 2015 Intland Software (support@intland.com)
 */

package com.intland.jenkins.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

import org.apache.commons.io.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intland.jenkins.api.dto.AttachmentDto;
import com.intland.jenkins.api.dto.MarkupDto;
import com.intland.jenkins.api.dto.RepositoryDto;
import com.intland.jenkins.api.dto.UserDto;

import jcifs.util.Base64;

public class CodebeamerApiClient {
    private final int HTTP_TIMEOUT = 10000;
    private HttpClient client;
    private RequestConfig requestConfig;
    private String baseUrl;
    private String wikiId;
    private ObjectMapper objectMapper;

    public CodebeamerApiClient(String username, String password, String url, String wikiIdentifier) {
        objectMapper = new ObjectMapper();
        wikiId = wikiIdentifier;
        baseUrl = url;

        // initialize rest client
        // http://stackoverflow.com/questions/9539141/httpclient-sends-out-two-requests-when-using-basic-auth
        final String authHeader = "Basic " + Base64.encode((username + ":" + password).getBytes(Charsets.UTF_8));

        HashSet<Header> defaultHeaders = new HashSet<Header>();
        defaultHeaders.add(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader));

        client = HttpClientBuilder
                .create()
                .setDefaultHeaders(defaultHeaders)
                .build();
        requestConfig = RequestConfig
                .custom()
                .setConnectionRequestTimeout(HTTP_TIMEOUT)
                .setConnectTimeout(HTTP_TIMEOUT)
                .setSocketTimeout(HTTP_TIMEOUT)
                .build();
    }

    public void createOrUpdateAttachment(String attachmentName, String newAttachmentContent) throws IOException {
        String attachmentId = getAttachmentId(attachmentName);
        if (attachmentId == null) {
            createAttachment(attachmentName, newAttachmentContent);
        } else {
            String oldAttachmentContent = getAttachmentContent(attachmentId);
            updateAttachment(attachmentId, attachmentName, oldAttachmentContent, newAttachmentContent);
        }
    }

    public String getWikiMarkup() throws IOException {
        String tmpUrl = String.format("%s/rest/wikipage/%s", baseUrl, wikiId);
        String json = getWithResultCheck(tmpUrl);
        MarkupDto markupDto = objectMapper.readValue(json, MarkupDto.class);
        return markupDto.getMarkup();
    }

    public void updateWikiMarkup(String url, String wikiId, String markup) throws IOException {
        HttpPut put = new HttpPut(String.format("%s/rest/wikipage", url));
        MarkupDto markupDto = new MarkupDto("/wikipage/"+wikiId, markup);
        String content = objectMapper.writeValueAsString(markupDto);

        StringEntity stringEntity = new StringEntity(content,"UTF-8");
        stringEntity.setContentType("application/json");
        put.setEntity(stringEntity);
        client.execute(put);
        put.releaseConnection();
    }

    public String getUserId(String author)  throws IOException {
        String authorNoSpace = author.replaceAll(" ", "");
        String tmpUrl = String.format("%s/rest/user/%s", baseUrl, authorNoSpace);

        //Fetch Page
        String httpResult = get(tmpUrl);
        String result = null;

        if (httpResult != null) { //20X success
            UserDto userDto = objectMapper.readValue(httpResult, UserDto.class);
            String uri = userDto.getUri();
            result = uri.substring(uri.lastIndexOf("/") + 1);
        }

        return result;
    }

    public String getCodeBeamerRepoUrlForGit(String repoUrl) throws IOException {
        // Name of Git repository is the string after the last /
        String[] segments = repoUrl.split("/");
        String name = segments[segments.length - 1];
        try {
            String requestUrl = String.format("%s/git/%s", baseUrl, name);
            String json = get(requestUrl);
            if (json != null) {
                RepositoryDto repositoryDto = objectMapper.readValue(json, RepositoryDto.class);
                return String.format("[%s%s]", baseUrl, repositoryDto.getUri());
            }
        } catch (IOException ex) {
            // TODO: logging
        }
        return "not managed by codeBeamer";
    }

    public String getCodeBeamerRepoUrlForSVN(String remote) {
        // We don't now for sure which part of the string is the name of the repository so we have to try until we succeed
        String[] segments = remote.split("/");
        // 0 = 'svn:' or 'http(s):', 1 = '', 2 = hostname
        for (int i = 3; i < segments.length; ++i) {
            String segment = segments[i];
            try {
                String requestUrl = String.format("%s/svn/%s", baseUrl, segment);
                String json = get(requestUrl);
                if (json == null) {
                    continue;
                }
                RepositoryDto repositoryDto = objectMapper.readValue(json, RepositoryDto.class);
                return String.format("[%s%s]", baseUrl, repositoryDto.getUri());
            } catch (IOException ex) {
                continue;
            }
        }
        return "not managed by codeBeamer";
    }

    private String getAttachmentId(String attachmentName) throws IOException {
        String url = String.format("%s/rest/wikipage/%s/attachments", baseUrl, wikiId);
        String attachmentResponse = get(url);

        AttachmentDto[] attachments = objectMapper.readValue(attachmentResponse, AttachmentDto[].class);
        String result = null;
        for (AttachmentDto attachmentDto : attachments) {
            if (attachmentDto.getName().equals(attachmentName)) {
                result = attachmentDto.getId();
                break;
            }
        }

        return result;
    }

    private String getAttachmentContent(String attachmentId) throws IOException {
        String url = String.format("%s/rest/attachment/%s/content", baseUrl, attachmentId);
        return get(url);
    }

    private boolean createAttachment(String attachmentName, String content) throws IOException {
        HttpPost post = new HttpPost(baseUrl + "/rest/attachment");
        FileBody fileBody = new FileBody(createTempFile(content, null));

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.STRICT);
        String jsonContent = String.format("{\"parent\" : \"/wikipage/%s\", \"name\": \"%s\"}", wikiId, attachmentName);
        builder.addTextBody("body", jsonContent, ContentType.APPLICATION_JSON);
        builder.addPart(attachmentName, fileBody);
        HttpEntity entity = builder.build();

        post.setEntity(entity);
        client.execute(post);
        post.releaseConnection();
        return true;
    }

    private boolean updateAttachment(String attachmentId, String attachmentName, String oldContent, String newContent) throws IOException {
        HttpPut put = new HttpPut(baseUrl + "/rest/attachment");
        FileBody fileBody = new FileBody(createTempFile(newContent, oldContent));
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.STRICT);
        String jsonContent = String.format("{\"uri\" : \"/attachment/%s\"}", attachmentId);
        builder.addTextBody("body", jsonContent, ContentType.APPLICATION_JSON);
        builder.addPart(attachmentName, fileBody);
        HttpEntity entity = builder.build();

        put.setEntity(entity);
        client.execute(put);
        put.releaseConnection();
        return true;
    }

    private File createTempFile(String newContent, String oldContent) throws IOException{
        final File tempFile = File.createTempFile("tmpfile", "csv");
        tempFile.deleteOnExit();
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(tempFile);
            fileWriter.append(newContent);
            if (oldContent != null) {
                fileWriter.append(oldContent);
            }
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                System.out.println("Error while flushing/closing fileWriter !!!");
                e.printStackTrace();
            }
        }

        return tempFile;
    }

    private String get(String url) throws IOException {
        HttpGet get = null;
        String result = null;
        try {
        	get = new HttpGet(url);
	        get.setConfig(requestConfig);
	        HttpResponse response = client.execute(get);
	        int statusCode = response.getStatusLine().getStatusCode();
	
	        if (statusCode == 200) {
	            result = new BasicResponseHandler().handleResponse(response);
	        }
	
	        get.releaseConnection();
	
        } finally {
        	if (get != null) {
    			get.releaseConnection();
    		}
        }
        return result;
    }

    private String getWithResultCheck(String url) throws IOException {
    	String result = "";
    	HttpGet get = null;
    	try {
	        get = new HttpGet(url);
	        get.setConfig(requestConfig);
	        HttpResponse response = client.execute(get);
	        int statusCode = response.getStatusLine().getStatusCode();
	
	        if (statusCode == 200) {
	            result = new BasicResponseHandler().handleResponse(response);
	        } else {
	        	throw new IOException(String.format("Could not connect to host: %s", url));
	        }
    	} finally {
    		if (get != null) {
    			get.releaseConnection();
    		}
    	}
        return result;
    }
}
