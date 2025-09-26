package com.prod_mas.app;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import jakarta.annotation.PostConstruct;

import java.net.InetAddress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.prod_mas")
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @PostConstruct
    @SuppressWarnings("CallToPrintStackTrace")
    public void startAgents() {
        new Thread(() -> {
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
                System.setProperty("main_host", host);
            } catch (Exception e) {
                host = "127.0.0.1";
            }
            profile.setParameter(Profile.MAIN, "true");
            profile.setParameter(Profile.MAIN_HOST, host);
            profile.setParameter(Profile.LOCAL_PORT, "1099");
            profile.setParameter(Profile.MAIN_PORT, "1099");
            profile.setParameter(Profile.GUI, "false");
            profile.setParameter(Profile.LOCAL_HOST, host);

            ContainerController container = rt.createMainContainer(profile);
            try {
                AgentController scheduler = container.createNewAgent("scheduler", "com.prod_mas.agents.Scheduler", null);
                scheduler.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
