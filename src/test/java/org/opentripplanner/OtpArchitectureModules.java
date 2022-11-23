package org.opentripplanner;

import org.opentripplanner._support.arch.Module;
import org.opentripplanner._support.arch.Package;

public interface OtpArchitectureModules {
  /* Third party libs*/

  Package GNU_TROVE = Package.of("gnu.trove.(*)..");
  Package JACKSON_ANNOTATIONS = Package.of("com.fasterxml.jackson.annotation");
  Package JTS_GEOM = Package.of("org.locationtech.jts.(*)..");

  /* OTP Modules */

  Package OTP_ROOT = Package.of("org.opentripplanner");
  Package FRAMEWORK = OTP_ROOT.subPackage("framework");
  Package UTIL = OTP_ROOT.subPackage("util");
  Package GEO_UTIL = OTP_ROOT.subPackage("common.geometry");

  Package RAPTOR_ADAPTER = OTP_ROOT
    .subPackage("routing")
    .subPackage("algorithm")
    .subPackage("raptoradapter");
  Package RAPTOR_ADAPTER_API = RAPTOR_ADAPTER.subPackage("api");

  /**
   * This is a bag of TRUE util classes - no dependencies to other OTP classes of frameworks.
   * The {@link #UTIL} packages needs cleanup, it contains model, framework and API classes.
   * The strategy is therefore to move the true util classes into sub packages, and then later
   * to move the reminding classes to the places they belong.
   */
  Module UTILS = Module.of(
    FRAMEWORK.subPackage("text"),
    FRAMEWORK.subPackage("time"),
    UTIL.subPackage("lang"),
    UTIL.subPackage("logging"),
    UTIL.subPackage("resources"),
    UTIL.subPackage("geometry")
  );

  Package TRANSIT = OTP_ROOT.subPackage("transit");
  Package TRANSIT_MODEL = TRANSIT.subPackage("model");

  /* The Raptor module */
  Package RAPTOR_ROOT = OTP_ROOT.subPackage("raptor");
  Package RAPTOR_API = RAPTOR_ROOT.subPackage("api.(*)..");
}
