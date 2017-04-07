package com.blasco991.simpleElections.controller;

import com.blasco991.simpleElections.model.Model;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VoteLoader extends Thread {
    private final List<String> parties;
    private final Model model;

    public VoteLoader(List<String> parties, Model model) {
        this.parties = parties;
        this.model = model;
    }

    public VoteLoader(Model model) {
        this.parties = new ArrayList<>(0);
        this.model = model;
    }

    @Override
    public void run() {
        try {
            String uri = "/SendVotes";
            if (!this.parties.isEmpty())
                uri += "?howmany=" + parties.size() * ThreadLocalRandom.current().nextInt(100) + "&parties=" + URLEncoder.encode(parties.stream().collect(Collectors.joining(",")), StandardCharsets.UTF_8.displayName());

            URL url = new URL("http://dev.blasco991.com" + uri);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200)
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            String string = br.lines().reduce(String::concat).orElse("");

            Map<String, Long> result = Arrays.stream(string.substring(1, string.length() - 1).split(", "))
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            EventQueue.invokeLater(() -> model.importVotes(result));

            conn.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}