package org.opentripplanner.standalone.config.updaters;

import org.opentripplanner.standalone.config.NodeAdapter;
import org.opentripplanner.standalone.config.updaters.sources.VehicleRentalSourceFactory;
import org.opentripplanner.updater.vehicle_rental.VehicleRentalUpdaterParameters;

public class BikeRentalUpdaterConfig {

  public static VehicleRentalUpdaterParameters create(String configRef, NodeAdapter c) {
    String sourceType = c.asText("sourceType");
    return new VehicleRentalUpdaterParameters(
        configRef + "." + sourceType,
        c.asText("networks", null),
        c.asInt("frequencySec", 60),
        VehicleRentalSourceFactory.create(sourceType, c)
    );
  }
}
