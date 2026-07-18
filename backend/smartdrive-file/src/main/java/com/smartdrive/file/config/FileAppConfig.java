package com.smartdrive.file.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("appConfig")
@Data
public class FileAppConfig {
    @Value("${project.folder}")
    private String projectFolder;
}
