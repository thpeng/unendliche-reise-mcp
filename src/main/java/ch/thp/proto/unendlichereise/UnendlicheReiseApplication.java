package ch.thp.proto.unendlichereise;

import ch.thp.proto.unendlichereise.locationinfo.LocationInfoTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UnendlicheReiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnendlicheReiseApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider locationInfoTools(LocationInfoTool tool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build();
    }
}
