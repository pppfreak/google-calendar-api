package com.api.controllers;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;

@Controller
public class GoogleCalController {

    private final static Log logger = LogFactory.getLog(GoogleCalController.class);
    private static final String APPLICATION_NAME = "Google Calender APi";
    private static HttpTransport httpTransport;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    GoogleAuthorizationCodeFlow flow;

    @Value("${google.client.client-id}")
    private String clientId;
    @Value("${google.client.client-secret}")
    private String clientSecret;
    @Value("${google.client.redirectUri}")
    private String redirectURI;

    final DateTime currentDate = new DateTime(System.currentTimeMillis());


    @RequestMapping(value = "/login/google", method = RequestMethod.GET)
    public RedirectView googleConnectionStatus(HttpServletRequest request) throws Exception {
        return new RedirectView(authorize());
    }

    @RequestMapping(value = "/Callback", method = RequestMethod.GET, params = "code")
    public ResponseEntity<String> oauth2Callback(@RequestParam(value = "code") String code) {
        com.google.api.services.calendar.model.Events eventList;
        StringBuilder message = new StringBuilder();
        try {
            TokenResponse response = flow
                    .newTokenRequest(code)
                    .setRedirectUri(redirectURI)
                    .execute();

            Credential credential = flow.createAndStoreCredential(response , "userID");
            com.google.api.services.calendar.Calendar client =
                    new com.google.api.services.calendar.Calendar.Builder(httpTransport , JSON_FACTORY , credential)
                            .setApplicationName(APPLICATION_NAME)
                            .build();

            Events events = client.events();
            eventList = events
                    .list("primary")
                    .setTimeMax(currentDate)
                    .execute();

            List<Event> items = eventList.getItems();

            if (items.isEmpty()) {

                message.append("No upcoming events found.");

            } else {
                message.append("Upcoming events  ");

                for (Event event: items) {
                    DateTime start = event
                            .getStart()
                            .getDateTime();
                    if (start == null) {
                        start = event
                                .getStart()
                                .getDate();
                    }

                    message
                            .append(getTime(start).substring(0 , 5))
                            .append(" - ")
                            .append(getTime(event
                                    .getEnd()
                                    .getDateTime()))
                            .append(" : ")
                            .append(event.getSummary())
                            .append("\n");

                }
            }

            // -----> Bonus Point: print available time slot
            System.out.println("---- Available time slot ----");
            getAvailableTimeSlot(eventList);

        } catch (Exception e) {
            logger.warn(
                    "Exception while handling OAuth2 callback (" + e.getMessage() + ")." + " Redirecting to google connection status page.");
            message
                    .append("Exception while handling OAuth2 callback (")
                    .append(e.getMessage())
                    .append(").")
                    .append(" Redirecting to google connection status page.");
        }

        return new ResponseEntity<>(message.toString() , HttpStatus.OK);
    }

    public static void getAvailableTimeSlot(com.google.api.services.calendar.model.Events events) {

        List<Event> items = events.getItems();
        String firstEvent = getTime(items
                .get(0)
                .getEnd()
                .getDateTime());
        int firstEventEndTime = 0, nextEventStartTime = 0;

        for (int i = 1; i < items.size(); i++) {
            String nextEvent = getTime(items
                    .get(i)
                    .getStart()
                    .getDateTime());

            for (int j = 0; j < 5; j++) {
                firstEventEndTime += firstEvent.charAt(j);
            }
            for (int k = 0; k < 5; k++) {
                nextEventStartTime += nextEvent.charAt(k);
            }

            if (firstEventEndTime != nextEventStartTime) {
                System.out.println(firstEvent + " - " + nextEvent);
            }


            firstEvent = nextEvent;
        }
    }


    public static String getTime(DateTime start) {

        Date date = new Date(start.getValue() + start.getTimeZoneShift());
        SimpleDateFormat dateFormat = new SimpleDateFormat("hh.mm aa");
        return dateFormat.format(date);
    }


    private String authorize() throws Exception {
        AuthorizationCodeRequestUrl authorizationUrl;
        if (flow == null) {
            Details web = new Details();
            web.setClientId(clientId);
            web.setClientSecret(clientSecret);
            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(web);
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            flow          = new GoogleAuthorizationCodeFlow.Builder(httpTransport , JSON_FACTORY , clientSecrets ,
                    Collections.singleton(CalendarScopes.CALENDAR_READONLY)).build();
        }
        authorizationUrl = flow
                .newAuthorizationUrl()
                .setRedirectUri(redirectURI);
        return authorizationUrl.build();
    }
}