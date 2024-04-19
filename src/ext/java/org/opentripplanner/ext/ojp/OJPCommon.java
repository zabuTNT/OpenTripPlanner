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
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import java.util.stream.Collectors;

public class OJPCommon {

  public List<String> allOTPModes = Arrays.asList("TRAM","SUBWAY","RAIL","BUS","FERRY","GONDOLA","FUNICULAR");
  public static String convertOTPModes(VehicleModesOfTransportEnumeration ojpMode) {
    switch(ojpMode) {
      case RAIL: return "RAIL";
      case BUS: return "BUS";
      case METRO: return "SUBWAY";
      case TRAM: return "TRAM";
      case WATER_TRANSPORT:
      case FERRY_SERVICE:
        return "FERRY";
      case TELECABIN: return "GONDOLA";
      case FUNICULAR: return "FUNICULAR";
      default: return null;
    }
  }
  public static VehicleModesOfTransportEnumeration convertOJPModes(int routeType) {
    if (routeType >= 100 && routeType < 200) { // Railway Service
      return VehicleModesOfTransportEnumeration.RAIL;
    } else if (routeType >= 200 && routeType < 300) { //Coach Service
      return VehicleModesOfTransportEnumeration.BUS;
    } else if (routeType >= 300
      && routeType < 500) { //Suburban Railway Service and Urban Railway service
      if (routeType >= 401 && routeType <= 402) {
        return VehicleModesOfTransportEnumeration.METRO;
      }
      return VehicleModesOfTransportEnumeration.RAIL;
    } else if (routeType >= 500 && routeType < 700) { //Metro Service and Underground Service
      return VehicleModesOfTransportEnumeration.METRO;
    } else if (routeType >= 700 && routeType < 900) { //Bus Service and Trolleybus service
      return VehicleModesOfTransportEnumeration.BUS;
    } else if (routeType >= 900 && routeType < 1000) { //Tram service
      return VehicleModesOfTransportEnumeration.TRAM;
    } else if (routeType >= 1000 && routeType < 1100) { //Water Transport Service
      return VehicleModesOfTransportEnumeration.WATER_TRANSPORT;
    } else if (routeType >= 1100 && routeType < 1200) { //Air Service
      return VehicleModesOfTransportEnumeration.AIR;
    } else if (routeType >= 1200 && routeType < 1300) { //Ferry Service
      return VehicleModesOfTransportEnumeration.FERRY_SERVICE;
    } else if (routeType >= 1300 && routeType < 1400) { //Telecabin Service
      return VehicleModesOfTransportEnumeration.TELECABIN;
    } else if (routeType >= 1400 && routeType < 1500) { //Funicalar Service
      return VehicleModesOfTransportEnumeration.FUNICULAR;
    }
    /* Original GTFS route types. Should these be checked before TPEG types? */
    switch (routeType) {
      case 0:
        return VehicleModesOfTransportEnumeration.TRAM;
      case 1:
        return VehicleModesOfTransportEnumeration.METRO;
      case 2:
        return VehicleModesOfTransportEnumeration.RAIL;
      case 3:
        return VehicleModesOfTransportEnumeration.BUS;
      case 4:
        return VehicleModesOfTransportEnumeration.FERRY_SERVICE;
      case 5:
        return VehicleModesOfTransportEnumeration.TRAM;
      case 6:
        return VehicleModesOfTransportEnumeration.TELECABIN;
      case 7:
        return VehicleModesOfTransportEnumeration.FUNICULAR;
      default:
        return VehicleModesOfTransportEnumeration.UNKNOWN;
    }
  }

  public static VehicleModesOfTransportEnumeration getTraverseMode(Route route) {
    int routeType = route.getGtfsType();
    return convertOJPModes(routeType);
  }

  public static List<VehicleModesOfTransportEnumeration> getTraverseModes(Set<Route> routes) {
    return routes.stream().map(r -> getTraverseMode(r)).distinct().collect(Collectors.toList());
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
