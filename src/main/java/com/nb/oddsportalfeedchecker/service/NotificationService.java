package com.nb.oddsportalfeedchecker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nb.oddsportalfeedchecker.dto.Post;
import com.nb.oddsportalfeedchecker.dto.PostDetails;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

    public static final String ODDS_FEED_FOLLOWING_URL = "https://www.oddsportal.com/ajax-communityFeed/following/42570701/";
    public static final String X_REQUESTED_WITH_HEADER_VALUE = "XMLHttpRequest";
    public static final String COOKIE_ODDSPORTAL_SESSION_PREFIX = "oddsportalcom_session=";
    public static final String ODDSPORTAL_LOGIN_URL = "https://www.oddsportal.com/userLogin";
    public static final String LOGIN_USERNAME = "login-username";
    public static final String LOGIN_PASSWORD = "login-password";
    private boolean shouldSendNotifications = true;
    @Value("${notification.email}")
    private String destinationEmail;
    @Value("${spring.mail.username}")
    private String originMail;
    @Value("#{'${include.sports}'.split(',')}")
    private List<String> sportsIncluded;

    @PostConstruct
    private void postConstructMethod() {
        sportsIncluded = sportsIncluded.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toList());
    }

    private final Long START_TIME_IN_SECONDS = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    private Post lastPostRecorded;

    private String loginSession;
    private long loginSessionExpiry;

    @Autowired
    private JavaMailSender emailSender;

    public String disableNotifications() {
        if (shouldSendNotifications) {
            this.shouldSendNotifications = false;
            return "Notifications disabled! No more e-mails will be send to " + destinationEmail + " until enabled explicitly";
        }
        return "Notifications already disabled!";
    }

    public String enableNotifications() {
        if (!shouldSendNotifications) {
            this.shouldSendNotifications = true;
            return "Notifications enabled! E-mails will be send to " + destinationEmail + " when needed!";
        }
        return "Notifications already enabled!";
    }

    public void login() {

        Map<String, String> bodyMap = new HashMap();
        bodyMap.put(LOGIN_USERNAME, "vakaroni3");
        bodyMap.put(LOGIN_PASSWORD, "unforgiven93!!!");

        WebClient client = WebClient.builder()
                .baseUrl(ODDSPORTAL_LOGIN_URL)
                .build();

        ResponseCookie cookie = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(bodyMap))
                .exchangeToMono(response -> {
                    if (!response.statusCode().is5xxServerError() && !response.statusCode().is4xxClientError()) {
                        return Mono.just(response.cookies().getFirst("oddsportalcom_session"));
                    }
                    return response.createError();
                })
                .block();

        loginSession = cookie.getValue();
        loginSessionExpiry = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + cookie.getMaxAge().toSeconds();
    }

    @Scheduled(fixedRateString = "${seconds.to.check}", timeUnit = TimeUnit.SECONDS)
    public void callOddsFeed() {
        if (!shouldSendNotifications) {
            log.info("Notifications currently disabled - skip checking the feed!");
            return;
        }
        //if we have no loginSession set or the login session has expired we must perform a new login and set new values for loginSession and loginSessionExpiry
        if (loginSession == null || TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) > loginSessionExpiry) {
            login();
        }
        Post post = getLastPostFromFeed();
        sendEmailIfNecessary(post);
        lastPostRecorded = post;
    }

    private void sendEmailIfNecessary(Post post) {
        if (lastPostRecorded == null) {
            //If there's no post recorded - the program has just started
            if (post.getInsertTime() > START_TIME_IN_SECONDS) {
                //If the last post in the feed is added after the first check - new email must be sent
                log.info("Program started in: " + ZonedDateTime.ofInstant(Instant.ofEpochSecond(START_TIME_IN_SECONDS), ZoneId.systemDefault())
                        + " Last message in thread is posted on: " + ZonedDateTime.ofInstant(Instant.ofEpochSecond(post.getInsertTime()), ZoneId.systemDefault()));
                if(!sportsIncluded.contains(post.getPostDetails().getSport().trim().toLowerCase())) {
                    log.info(post.getPostDetails().getSport() + " is not included in the list of sports that we send notifications for!");
                    log.info("We only send emails for events of type: " + String.join(",", sportsIncluded));
                    return;
                }
                sendEmail(post);
            } else {
                log.info("No new relevant posts in the feed after the program has started! No mail needs to be send!");
            }
        } else {
            //If there's a post recorded - the program is running for a while
            if (post.getInsertTime() > lastPostRecorded.getInsertTime()) {
                //We must check whether the last post recoded in the app is earlier than the last post found on the feed - if so an email must be sent
                log.info("Last message recorded in: " + ZonedDateTime.ofInstant(Instant.ofEpochSecond(lastPostRecorded.getInsertTime()), ZoneId.systemDefault()) +
                        " Last message in thread is posted on: " + ZonedDateTime.ofInstant(Instant.ofEpochSecond(post.getInsertTime()), ZoneId.systemDefault()));
                if(!sportsIncluded.contains(post.getPostDetails().getSport().trim().toLowerCase())) {
                    log.info(post.getPostDetails().getSport() + " is not included in the list of sports that we send notifications for!");
                    log.info("We only send emails for events of type: " + String.join(",", sportsIncluded));
                    return;
                }
                sendEmail(post);
            } else {
                log.info("No new relevant posts in the feed since the last check! No mail needs to be send!");
            }
        }
    }

    private Post getLastPostFromFeed() {
        WebClient webClient = WebClient.create();
        Post post = webClient.get()
                .uri(ODDS_FEED_FOLLOWING_URL)
                .header("X-Requested-With", X_REQUESTED_WITH_HEADER_VALUE)
                .header("Cookie", COOKIE_ODDSPORTAL_SESSION_PREFIX + loginSession)
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToMono(String.class).map(NotificationService::findFirstPost);
                    } else {
                        return response.createError();
                    }
                })
                .block();
        return post;
    }

    @SneakyThrows
    private static Post findFirstPost(String e) {
        ObjectMapper mapper = new ObjectMapper();
        Post post = mapper.treeToValue(mapper.readTree(e).get("d").get("feed").elements().next(), Post.class);
        PostDetails postDetails = mapper.treeToValue(mapper.readTree(e).get("d").get("info").get(post.getSubjectUID().toString()), PostDetails.class);
        post.setPostDetails(postDetails);
        return post;
    }

    private void sendEmail(Post post) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(originMail);
        message.setTo(destinationEmail);
        message.setSubject(this.buildSubject(post));
        message.setText(this.buildMessage(post));
        emailSender.send(message);
        log.info("Mail sent successfully! Please check your inbox!");
    }

    private String buildMessage(Post post) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please check your personal feed, there's a new message with the following details:")
                .append(System.getProperty("line.separator")).append("Post time: ").append(ZonedDateTime.ofInstant(Instant.ofEpochSecond(post.getInsertTime()), ZoneId.systemDefault()))
                .append(System.getProperty("line.separator")).append("Sport: ").append(post.getPostDetails().getSport())
                .append(System.getProperty("line.separator")).append("Event name: ").append(post.getPostDetails().getEventName())
                .append(System.getProperty("line.separator")).append("Country: ").append(post.getPostDetails().getCountry())
                .append(System.getProperty("line.separator")).append("League: ").append(post.getPostDetails().getLeague())
                .append(System.getProperty("line.separator")).append("Event Start Date: ").append(post.getPostDetails().getStartDateText())
                .append(System.getProperty("line.separator")).append(System.getProperty("line.separator"))
                .append("Check it out at: ").append("https://www.oddsportal.com/community/feed#");
        return sb.toString();
    }

    private String buildSubject(Post post) {
        StringBuilder sb = new StringBuilder();
        sb
                .append("New Post Found! ")
                .append(post.getPostDetails().getSport())
                .append(", ")
                .append(post.getPostDetails().getEventName())
                .append(", ")
                .append(post.getPostDetails().getCountry())
                .append(", ")
                .append(post.getPostDetails().getLeague());
        return sb.toString();
    }

}
