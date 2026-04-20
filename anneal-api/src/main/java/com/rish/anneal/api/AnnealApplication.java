package com.rish.anneal.api;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Anneal application entry point.
 *
 * Quarkus bootstraps CDI, REST, and all extensions automatically.
 * This class exists to give the application a named entry point
 * and a place to add startup logic if needed.
 */
@QuarkusMain
public class AnnealApplication implements QuarkusApplication {

    public static void main(String... args) {
        Quarkus.run(AnnealApplication.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }
}
