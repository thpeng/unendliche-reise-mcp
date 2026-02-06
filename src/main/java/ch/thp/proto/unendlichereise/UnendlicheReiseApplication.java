package ch.thp.proto.unendlichereise;

import ch.thp.proto.unendlichereise.locationinfo.LocationInfoTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

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

    @Bean
    SecurityWebFilterChain mcpSecurityDisabledSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(reg -> reg
                        // Everything else is public
                        .anyExchange().permitAll()
                )
                .build();
    }
}
