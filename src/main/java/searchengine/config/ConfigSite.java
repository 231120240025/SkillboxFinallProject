package searchengine.config;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ConfigSite {
    private String url;
    private String name;
    private int id; // If you want to link the ConfigSite to a Site entity in the database
    private String status; // If you want to define the status, e.g., ACTIVE, INACTIVE
    private String lastError; // To store any errors that might occur during site processing
}
