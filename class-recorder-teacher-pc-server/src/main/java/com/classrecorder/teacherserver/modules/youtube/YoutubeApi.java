package com.classrecorder.teacherserver.modules.youtube;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.classrecorder.teacherserver.modules.youtube.com.classrecorder.teacherserver.modules.youtube.exceptions.YoutubeApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.NotFoundException;

import com.classrecorder.teacherserver.server.properties.YoutubeApiProperties;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class YoutubeApi {

    public enum YoutubeUploaderState {
        STOPPED, 
        INITIATION_STARTED, 
        UPLOAD_IN_PROGRESS,
        FINISHED
    }
	
	private final Logger log = LoggerFactory.getLogger(this.getClass());
	
	//Objects needed to use de Youtube API
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static String OAUTH_URL_START = "https://accounts.google.com/o/oauth2/auth?";
    private static String VIDEO_FILE_FORMAT = "video/*";

	private static YouTube youtube;
    private static String oAuthUrl;
    private List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload",
        "https://www.googleapis.com/auth/youtube");
    private GoogleClientSecrets clientSecrets;
	private YoutubeApiProperties properties;

	private List<YoutubeOutputObserver> observers = new ArrayList<>();
    
    //State variables
    private YoutubeUploaderState state = YoutubeUploaderState.STOPPED;
    private double progress;
	
	public YoutubeApi(YoutubeApiProperties youtubeProperties) throws YoutubeApiException {
        this.properties = youtubeProperties;
        InputStream convertedJsonYtSecrets = ytPropertiesToJsonValidFormat(properties);
        try {
            this.clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, convertedJsonYtSecrets);
        }
        catch(IOException e) {
            throw new YoutubeApiException("Can't read clients secrets from application.properties");
        }
        this.oAuthUrl = createAuthUrl();
        this.state = YoutubeUploaderState.STOPPED;
	}
	
	private Credential authorize() throws YoutubeApiException, IOException {

		InputStream convertedJsonYtSecrets = ytPropertiesToJsonValidFormat(properties);
		// Load client secrets.

	    // Checks that the defaults have been replaced (Default = "Enter X here").
	    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
	        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {

	    	log.error("Set Client ID and Secret from https://code.google.com/apis/console/?api=youtube"
			  + "in your application.properties");
			log.error("Example:");
			log.error("youtube.client_id=<your_client_id> \n youtube.client_secret=<your_client_secret>");

	      throw new YoutubeApiException("Youtube Api keys are not set correctly");
	    }

	    // Set up file credential store.
	    FileCredentialStore credentialStore = new FileCredentialStore(
	        new File(System.getProperty("user.home"), ".credentials/youtube-api-uploadvideo.json"),
	        JSON_FACTORY);

	    // Set up authorization code flow.
	    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
	        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes).setCredentialStore(credentialStore)
	        .build();

	    // Build the local server and bind it to port 9000
	    LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(this.properties.getCallbackRedirectPort()).build();

		log.info("Youtube api authorized");
		log.info(createAuthUrl());
	    // Authorize.
	    return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");
	}

	private String createAuthUrl() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("client_id", clientSecrets.getDetails().getClientId());
        parameters.put("redirect_uri", "http://localhost:" + properties.getCallbackRedirectPort() + "/Callback");
        parameters.put("response_type", "code");
        String scopesParam = "";
        String finalUrl = OAUTH_URL_START;
        for(int i = 0; i < scopes.size(); i++) {
            if(i + 1 == scopes.size()) {
                scopesParam += scopes.get(i);
            } else {
                scopesParam += scopes.get(i) + "%20";
            }
        }
        parameters.put("scope", scopesParam);
        boolean firstParam = true;
        for(Entry<String, String> parameter: parameters.entrySet()) {
            if(firstParam) {
                finalUrl += parameter.getKey() + "=" + parameter.getValue();
                firstParam = false;
            }
            else {
                finalUrl += "&" + parameter.getKey() + "=" + parameter.getValue();
            }
        }
        return finalUrl;
    }

	private InputStream ytPropertiesToJsonValidFormat(YoutubeApiProperties properties) {
		Gson gson = new Gson();
		JsonObject jsonClientSecrets = new JsonObject();
		jsonClientSecrets.add("installed", new JsonObject());
		jsonClientSecrets.get("installed").getAsJsonObject().addProperty("client_id", properties.getClientId());
		jsonClientSecrets.get("installed").getAsJsonObject().addProperty("client_secret", properties.getClientSecret());
		return new ByteArrayInputStream(gson.toJson(jsonClientSecrets).getBytes());
    }
    
    private Video getYoutubeVideoById(String youtubeId) throws YoutubeApiException, IOException, NotFoundException {
        Credential credential = authorize();
        youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build();

        // Create the video list request
        YouTube.Videos.List listVideosRequest = youtube.videos().list(youtubeId, "snippet");

        // Request is executed and video list response is returned
        VideoListResponse listResponse = listVideosRequest.execute();

        List<Video> videoList = listResponse.getItems();
        if (videoList.isEmpty()) {
            log.error("Can't find a video with video id: " + youtubeId);
            throw new NotFoundException("Can't find a video with video id" + youtubeId);
        }

        // Since a unique video id is given, it will only return 1 video.
        Video video = videoList.get(0);
        return video;
    }

	public void addObserver(YoutubeOutputObserver observer) {
		this.observers.add(observer);
	}

	public void notifyObservers(String outputMessage) {
		for(YoutubeOutputObserver observer: observers) {
            try {
                observer.update(outputMessage);   
            }
            catch(IOException e) {
                log.error("Can't send youtube logs");
            }
		}
    }
    
    public YoutubeUploaderState getState() {
        return this.state;
    }

    public double getProgress() {
        return this.progress;
    }

    public static String getoAuthUrl() {
        return oAuthUrl;
    }
	
	public Video uploadVideo(YoutubeVideoInfo ytVideoInfo) throws Exception {
		Credential credential = authorize();
		youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build();
		File videoFile = new File(ytVideoInfo.getVideoPath());
		Video videoMetadata = new Video();
		
		VideoStatus status = new VideoStatus();
		status.setPrivacyStatus(ytVideoInfo.getPrivacyStatus());
		videoMetadata.setStatus(status);
		
		VideoSnippet snippet = new VideoSnippet();
		snippet.setTitle(ytVideoInfo.getVideoTitle());
		snippet.setDescription(ytVideoInfo.getDescription());
		snippet.setTags(ytVideoInfo.getTags());
		
		videoMetadata.setSnippet(snippet);
		
		InputStreamContent mediaContent = new InputStreamContent(
				VIDEO_FILE_FORMAT, new BufferedInputStream(new FileInputStream(videoFile)));
		mediaContent.setLength(videoFile.length());
		
		YouTube.Videos.Insert videoInsert = youtube.videos()
		          .insert("snippet,statistics,status", videoMetadata, mediaContent);
		
		MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
		uploader.setDirectUploadEnabled(false);
		
		MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {
	        public void progressChanged(MediaHttpUploader uploader) throws IOException {
                switch (uploader.getUploadState()) {
                    case INITIATION_STARTED:
                        notifyObservers(YoutubeUploaderState.INITIATION_STARTED.toString());
                        state = YoutubeUploaderState.INITIATION_STARTED;
                        break;
                    case INITIATION_COMPLETE:
                        notifyObservers(YoutubeUploaderState.UPLOAD_IN_PROGRESS.toString());
                        state = YoutubeUploaderState.UPLOAD_IN_PROGRESS;
                        break;
                    case MEDIA_IN_PROGRESS:
                        notifyObservers("Percentage: " + uploader.getProgress());
                        state = YoutubeUploaderState.UPLOAD_IN_PROGRESS;
                        progress = uploader.getProgress();
                        break;
                    case MEDIA_COMPLETE:
                        notifyObservers(YoutubeUploaderState.FINISHED.toString());
                        state = YoutubeUploaderState.STOPPED;
                        break;
                    case NOT_STARTED:
                        notifyObservers(YoutubeUploaderState.STOPPED.toString());
                        state = YoutubeUploaderState.STOPPED;
                        break;
                }
	        }
	      };
	      uploader.setProgressListener(progressListener);
          Video video = videoInsert.execute();
          return video;
    }
    
    public Video updateVideo(String youtubeId, YoutubeVideoInfo ytVideoInfo) throws Exception {
        Video video = getYoutubeVideoById(youtubeId);
        VideoSnippet snippet = video.getSnippet();

        // Proceed to edit video information
        snippet.setTitle(ytVideoInfo.getVideoTitle());
        snippet.setDescription(ytVideoInfo.getDescription());
        snippet.setTags(ytVideoInfo.getTags());

        // Create the video update request
        YouTube.Videos.Update updateVideosRequest = youtube.videos().update("snippet", video);

        // Request is executed and updated video is returned
        Video videoResponse = updateVideosRequest.execute();
        return videoResponse;
    }

    public boolean deleteVideo(String youtubeId) {
        try {
            Credential credential = authorize();
            youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build();
            YouTube.Videos.Delete deleteVideoRequest = youtube.videos().delete(youtubeId);
            deleteVideoRequest.execute();
            return true;
        } 
        catch(Exception e) {
            return false;
        }
    }
}
