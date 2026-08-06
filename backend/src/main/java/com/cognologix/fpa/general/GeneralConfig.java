package com.cognologix.fpa.general;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "general_config")
@Getter
@Setter
@NoArgsConstructor
public class GeneralConfig {

    public static final String DATE_FORMAT_KEY = "date_format";
    public static final String JWT_EXPIRY_HOURS_KEY = "jwt_expiry_hours";
    public static final String INACTIVITY_TIMEOUT_MINUTES_KEY = "inactivity_timeout_minutes";

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;
}
