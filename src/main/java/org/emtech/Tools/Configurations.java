package org.emtech.Tools;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Component
public class Configurations {
    Properties prop;

    public Configurations() {
    }
    public Properties getProperties() {
        prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find application.properties");
            } else {
                prop.load(input);
            }
        } catch (IOException e) {
            System.err.println("Error loading properties: " + e.getMessage());
        }
        return prop;
    }
}