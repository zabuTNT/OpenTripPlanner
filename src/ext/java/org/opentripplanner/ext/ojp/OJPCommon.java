package org.opentripplanner.ext.ojp;

import de.vdv.ojp.model.VehicleModesOfTransportEnumeration;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.ext.restapi.mapping.FeedScopedIdMapper;
import org.opentripplanner.framework.geometry.EncodedPolyline;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import java.util.stream.Collectors;

public class OJPCommon {

  public static String convertOTPModes(VehicleModesOfTransportEnumeration ojpMode) {
    switch (ojpMode) {
      case RAIL:
        return "RAIL";
      case BUS:
        return "BUS";
      case METRO:
        return "SUBWAY";
      case TRAM:
        return "TRAM";
      case WATER_TRANSPORT:
      case FERRY_SERVICE:
        return "FERRY";
      case TELECABIN:
        return "GONDOLA";
      case FUNICULAR:
        return "FUNICULAR";
      default:
        return null;
    }
  }

  public static VehicleModesOfTransportEnumeration convertOJPModes(TransitMode routeType) {
    switch (routeType) {
      case TransitMode.RAIL:
        return VehicleModesOfTransportEnumeration.RAIL;
      case TransitMode.BUS:
        return VehicleModesOfTransportEnumeration.BUS;
      case TransitMode.SUBWAY:
        return VehicleModesOfTransportEnumeration.METRO;
      case TransitMode.TRAM:
        return VehicleModesOfTransportEnumeration.TRAM;
      case TransitMode.FERRY:
        return VehicleModesOfTransportEnumeration.WATER_TRANSPORT;
      case TransitMode.GONDOLA:
        return VehicleModesOfTransportEnumeration.TELECABIN;
      case TransitMode.FUNICULAR:
        return VehicleModesOfTransportEnumeration.FUNICULAR;
      case AIRPLANE:
        return VehicleModesOfTransportEnumeration.AIR;
      case TAXI:
        return VehicleModesOfTransportEnumeration.TAXI;
      default:
        return VehicleModesOfTransportEnumeration.UNKNOWN;
    }
  }

  public static VehicleModesOfTransportEnumeration getTraverseMode(Route route) {
    TransitMode routeType = route.getMode();
    return convertOJPModes(routeType);
  }

  public static List<VehicleModesOfTransportEnumeration> getTraverseModes(Set<Route> routes) {
    return routes.stream().map(OJPCommon::getTraverseMode).distinct().collect(Collectors.toList());
  }

  public static <T> T validateExist(String eName, T entity, String keyLabel, Object key) {
    if (entity != null) {
      return entity;
    } else {
      throw notFoundException(eName, keyLabel, key);
    }
  }

  public static FeedScopedId createId(String name, String value) {
    return FeedScopedIdMapper.mapToDomain(name, value);
  }

  private static NotFoundException notFoundException(String eName, String keyLbl, Object key) {
    return notFoundException(eName, keyLbl + ": " + key);
  }

  private static NotFoundException notFoundException(String entity, String details) {
    return new NotFoundException(entity + " not found. " + details);
  }

  public static List<Coordinate> decodePolyline(EncodedPolyline encoded) {
    List<Coordinate> coordinates = new ArrayList<>();

    int index = 0, len = encoded.length();
    int lat = 0, lng = 0;

    while (index < len) {
      int b, shift = 0, result = 0;
      do {
        b = encoded.points().charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lat += dlat;

      shift = 0;
      result = 0;
      do {
        b = encoded.points().charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lng += dlng;

      coordinates.add(new Coordinate(lat / 1E5, lng / 1E5));
    }

    return coordinates;
  }
}
