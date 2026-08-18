package com.calfus.ragassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes React Router's client-side routes work when Spring Boot is serving
 * the built frontend directly. Spring Boot already serves "/" -> index.html
 * and real files under /assets/** automatically (that's just static resource
 * serving) -- but a browser hitting /login or /dashboard directly (a fresh
 * load, or a refresh) has no matching file on disk, so without this it 404s
 * instead of loading index.html and letting React Router take over.
 *
 * The path pattern below matches a single segment with no "." in it (so
 * "/login" matches but "/assets/app.js" doesn't) and forwards it to
 * index.html instead. All of this app's routes (/login, /register,
 * /dashboard) are single-segment, so this is enough without pulling in a
 * heavier catch-all/regex setup.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
    }
}
