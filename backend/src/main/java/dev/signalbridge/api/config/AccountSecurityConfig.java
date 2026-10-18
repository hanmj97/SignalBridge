package dev.signalbridge.api.config;

import dev.signalbridge.api.auth.AccountService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class AccountSecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
  @Bean AuthenticationManager authenticationManager(AccountService accounts,PasswordEncoder encoder) {
    DaoAuthenticationProvider provider=new DaoAuthenticationProvider(accounts);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }
  @Bean SecurityContextRepository securityContextRepository() {return new HttpSessionSecurityContextRepository();}
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,SecurityContextRepository repository) throws Exception {
    HttpSessionCsrfTokenRepository tokens=new HttpSessionCsrfTokenRepository();
    tokens.setHeaderName("X-CSRF-TOKEN");
    http
      .cors(cors -> {})
      .csrf(csrf->csrf.csrfTokenRepository(tokens).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
      .securityContext(context->context.securityContextRepository(repository))
      .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
      .authorizeHttpRequests(auth->auth
         // Allow Spring MVC to render original HTTP error statuses on internal ERROR dispatch.
         // Without this rule, the final denyAll() can mask a 400/403 error as 401.
         .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
         .requestMatchers("/api/v1/account/**").authenticated()
         .requestMatchers("/api/v1/watches","/api/v1/watches/**").authenticated()
         .requestMatchers("/api/v1/my-repositories","/api/v1/my-repositories/**").authenticated()
         .requestMatchers("/api/v1/opportunity-alerts","/api/v1/opportunity-alerts/**").authenticated()
         .requestMatchers(HttpMethod.POST,"/api/v1/auth/logout").authenticated()
         .requestMatchers("/api/v1/auth/**").permitAll()
         .requestMatchers(HttpMethod.GET,"/api/v1/**", "/actuator/health", "/actuator/info").permitAll()
         .anyRequest().denyAll())
      .httpBasic(basic->basic.disable())
      .formLogin(form->form.disable())
      .logout(logout->logout.disable())
      .exceptionHandling(ex->ex.authenticationEntryPoint((req,res,error)->res.sendError(401)));
    return http.build();
  }
}
