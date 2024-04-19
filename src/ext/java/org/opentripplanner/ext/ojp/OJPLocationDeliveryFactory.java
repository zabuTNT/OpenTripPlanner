package org.opentripplanner.ext.ojp;

import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import de.vdv.ojp.model.BusSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.ErrorDescriptionStructure;
import de.vdv.ojp.model.FunicularSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.LocationStructure;
import de.vdv.ojp.model.MetroSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.NaturalLanguageStringStructure;
import de.vdv.ojp.model.RailSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.ServiceDeliveryErrorConditionStructure;
import de.vdv.ojp.model.StopPointRefStructure;
import de.vdv.ojp.model.TelecabinSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.TramSubmodesOfTransportEnumeration;
import de.vdv.ojp.model.VehicleModesOfTransportEnumeration;
import de.vdv.ojp.model.WaterSubmodesOfTransportEnumeration;

import de.vdv.ojp.InternationalTextStructure;
import de.vdv.ojp.ModeStructure;
import de.vdv.ojp.OJPLocationInformationDeliveryStructure;
import de.vdv.ojp.OJPLocationInformationRequestStructure;
import de.vdv.ojp.ObjectFactory;
import de.vdv.ojp.PlaceResultStructure;
import de.vdv.ojp.PlaceStructure;
import de.vdv.ojp.PlaceTypeEnumeration;
import de.vdv.ojp.StopPointStructure;
import org.opentripplanner.routing.graphfinder.GraphFinder;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;

public class OJPLocationDeliveryFactory {

  private final OJPLocationInformationRequestStructure informationRequest;
  private final OtpServerRequestContext serverRequestContext;
  private final GraphFinder graphFinder;
  private List<VehicleModesOfTransportEnumeration> restrictionModes;
  private String searchByName;
  private ObjectFactory factory;

  public OJPLocationDeliveryFactory(OtpServerRequestContext serverRequestContext, OJPLocationInformationRequestStructure informationRequest, ObjectFactory factory) {
    this.serverRequestContext = serverRequestContext;
    this.graphFinder = this.serverRequestContext.graphFinder();
    this.informationRequest = informationRequest;
    this.factory = factory;
  }

  private TransitService transitService() {
    return this.serverRequestContext.transitService();
  }

  private StopLocation stop(String stopId) {
    var stop = transitService().getRegularStop(OJPCommon.createId("stopId", stopId));
    return OJPCommon.validateExist("Stop", stop, "stopId", stop);
  }

  public OJPLocationInformationDeliveryStructure create() {
    OJPLocationInformationDeliveryStructure location = new OJPLocationInformationDeliveryStructure();

    long timeStart = System.currentTimeMillis();

    String stopCode = null;
    restrictionModes = new ArrayList<>();
    List<PlaceTypeEnumeration> restrictionTypes = new ArrayList<>();
    int continueAt = 0;
    boolean includePtModes = false;
    long maxResults = Integer.MAX_VALUE;

    if (informationRequest.getPlaceRef() != null) {
      if (informationRequest.getPlaceRef().getStopPointRef() != null) {
        stopCode = informationRequest.getPlaceRef().getStopPointRef().getValue();
      } else if (informationRequest.getPlaceRef().getStopPlaceRef() != null) {
        stopCode = informationRequest.getPlaceRef().getStopPlaceRef().getValue();
      }
    }


    if (informationRequest.getRestrictions() != null) {

      restrictionTypes = informationRequest.getRestrictions().getType();

      if (informationRequest.getRestrictions().getPtModes() != null) {
        restrictionModes = informationRequest.getRestrictions().getPtModes().getPtMode();
      }

      if (informationRequest.getRestrictions().isIncludePtModes() != null) {
        includePtModes = informationRequest.getRestrictions().isIncludePtModes();
      }


      if (informationRequest.getRestrictions().getContinueAt() != null) {
        continueAt = informationRequest.getRestrictions().getContinueAt().intValue();
      }

      if (informationRequest.getRestrictions().getNumberOfResults() != null) {
        maxResults = informationRequest.getRestrictions().getNumberOfResults().longValue();
      }

    }

    if (!restrictionTypes.isEmpty() && !restrictionTypes.contains(PlaceTypeEnumeration.STOP)) {
      //return error
      location.setStatus(false);
      ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
      ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
      descr.setValue("Illegal Type");
      long timeEnd = System.currentTimeMillis();
      location.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
      location.setErrorCondition(error.withDescription(descr));
      return location;
    }

    List<NearbyStop> tmpStops = new ArrayList<>();

    if (stopCode != null) {
      //search by id

      StopLocation stop = stop(stopCode);
      NearbyStop nearbyStop = new NearbyStop(stop, 1, null, null);
      tmpStops.add(nearbyStop);

    } else if (informationRequest.getInitialInput() != null) {
      searchByName = informationRequest.getInitialInput().getLocationName();

      if (informationRequest.getInitialInput().getGeoRestriction() != null) {
        //search by georestriction
        if (informationRequest.getInitialInput().getGeoRestriction().getCircle() != null) {
          //search by georestriction circle
          double latitude = informationRequest.getInitialInput().getGeoRestriction().getCircle().getCenter().getLatitude().doubleValue();
          double longitude = informationRequest.getInitialInput().getGeoRestriction().getCircle().getCenter().getLongitude().doubleValue();
          int radius = informationRequest.getInitialInput().getGeoRestriction().getCircle().getRadius().intValue();
          tmpStops = graphFinder.findClosestStops(new Coordinate(longitude, latitude), radius).stream().sorted(Comparator.comparing(s -> (float) s.distance)).collect(Collectors.toList());
        } else if (informationRequest.getInitialInput().getGeoRestriction().getRectangle() != null) {
          //search by georestriction rectangle
          Envelope envelope = new Envelope(
            new Coordinate(informationRequest.getInitialInput().getGeoRestriction().getRectangle().getLowerRight().getLongitude().doubleValue(),
              informationRequest.getInitialInput().getGeoRestriction().getRectangle().getLowerRight().getLatitude().doubleValue()),
            new Coordinate(informationRequest.getInitialInput().getGeoRestriction().getRectangle().getUpperLeft().getLongitude().doubleValue(),
              informationRequest.getInitialInput().getGeoRestriction().getRectangle().getUpperLeft().getLatitude().doubleValue())
          );

          tmpStops = transitService().findRegularStops(envelope)
            .stream()
            .map(t -> new NearbyStop(t, 1, null, null))
            .collect(Collectors.toList());
        }
      } else {
        //return all
        tmpStops = transitService().listRegularStops().stream().map(t -> new NearbyStop(t, 1, null, null))
          .collect(Collectors.toList());
      }
    } else {
      tmpStops = transitService().listRegularStops().stream().map(t -> new NearbyStop(t, 1, null, null))
        .collect(Collectors.toList());
    }


    float sumDistances = 0;

    List<NearbyStop> stops = tmpStops.stream().filter(mStop -> {
      //filter by modes
      if (!restrictionModes.isEmpty()) {
        Set<Route> routes = transitService().getRoutesForStop(mStop.stop);

        List<VehicleModesOfTransportEnumeration> types = OJPCommon.getTraverseModes(routes);

        if (Collections.disjoint(restrictionModes, types)) { //at list one mode must be in common
          return false;
        }
      }
      //filter by name

      if (searchByName != null) {
        Pattern p = Pattern.compile(searchByName);
        return p.matcher(mStop.stop.getName().toString()).matches();
      }

      return true;
    }).skip(continueAt).limit(maxResults).collect(Collectors.toList());

    BigInteger mContinueAt = BigInteger.valueOf(continueAt + stops.size());

    if (stops.isEmpty()) {
      location.setStatus(false);
      ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
      ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
      descr.setValue("No Locations");
      location.setErrorCondition(error.withDescription(descr));
      long timeEnd = System.currentTimeMillis();
      location.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
      return location;
    } else {
      location.setStatus(true);
      if (stops.size() == maxResults) {
        location.setContinueAt(mContinueAt);
      }
    }

    for (NearbyStop stop : stops) {
      double mDistance = stop.distance;
      if (mDistance == 0) {
        mDistance = (float) 0.01;
      }
      sumDistances += (float) (1 / mDistance);
    }


    for (NearbyStop stop : stops) {
      PlaceResultStructure place = new PlaceResultStructure();
      PlaceStructure p = new PlaceStructure();
      StopPointStructure sp = new StopPointStructure();
      InternationalTextStructure it = new InternationalTextStructure();
      NaturalLanguageStringStructure nL = new NaturalLanguageStringStructure();


      Collection<Agency> agencies = transitService()
        .getAgencies()
        .stream()
        .filter(agency -> agency.getId().getFeedId().equals(stop.stop.getId().getFeedId()))
        .collect(Collectors.toList());

      String lang = agencies.iterator().next().getLang();
      Locale langLocale = new Locale(lang);
      nL.setLang(lang);
      nL.setValue(stop.stop.getName().toString(langLocale));
      it.setText(nL);
      sp.setStopPointName(it);
      sp.setWheelchairAccessible(stop.stop.getWheelchairAccessibility() == Accessibility.POSSIBLE);
      LocationStructure geo = new LocationStructure();
      geo.setLatitude(BigDecimal.valueOf(stop.stop.getLat()));
      geo.setLongitude(BigDecimal.valueOf(stop.stop.getLon()));
      p.setGeoPosition(geo);
      StopPointRefStructure sRef = new StopPointRefStructure();
      sRef.setValue(stop.stop.getId().toString());
      sp.setStopPointRef(sRef);
      p.setStopPoint(sp);
      p.setLocationName(it);

      place.setLocation(p);

      if (includePtModes) {
        Set<Route> routes = transitService().getRoutesForStop(stop.stop);

        routes.stream().map(Route::getMode).distinct().forEach(type -> {
          ModeStructure m = new ModeStructure();
          switch (type) {
            case TRAM:
              m.setPtMode(VehicleModesOfTransportEnumeration.TRAM);
              m.setTramSubmode(TramSubmodesOfTransportEnumeration.ALL_TRAM_SERVICES);
              break;
            case SUBWAY:
              m.setPtMode(VehicleModesOfTransportEnumeration.METRO);
              m.setMetroSubmode(MetroSubmodesOfTransportEnumeration.ALL_RAIL_SERVICES);
              break;
            case RAIL:
              m.setPtMode(VehicleModesOfTransportEnumeration.RAIL);
              m.setRailSubmode(RailSubmodesOfTransportEnumeration.ALL_RAIL_SERVICES);
              break;
            case BUS:
              m.setPtMode(VehicleModesOfTransportEnumeration.BUS);
              m.setBusSubmode(BusSubmodesOfTransportEnumeration.ALL_BUS_SERVICES);
              break;
            case FERRY:
              m.setPtMode(VehicleModesOfTransportEnumeration.FERRY_SERVICE);
              m.setWaterSubmode(WaterSubmodesOfTransportEnumeration.ALL_WATER_TRANSPORT_SERVICES);
              break;
            case CABLE_CAR:
              m.setPtMode(VehicleModesOfTransportEnumeration.TRAM);
              m.setTramSubmode(TramSubmodesOfTransportEnumeration.CITY_TRAM);
              break;
            case GONDOLA:
              m.setPtMode(VehicleModesOfTransportEnumeration.TELECABIN);
              m.setTelecabinSubmode(TelecabinSubmodesOfTransportEnumeration.ALL_TELECABIN_SERVICES);
              break;
            case FUNICULAR:
              m.setPtMode(VehicleModesOfTransportEnumeration.FUNICULAR);
              m.setFunicularSubmode(FunicularSubmodesOfTransportEnumeration.ALL_FUNICULAR_SERVICES);
              break;
            default:
              m.setPtMode(VehicleModesOfTransportEnumeration.UNKNOWN);
              break;
          }
          place.getMode().add(m);
        });
      }

      place.setComplete(true);
      //   1/distance / sum(1/distance)

      double mDistance = stop.distance;
      if (mDistance == 0) { // cannot be 0
        mDistance = (float) 0.01;
      }

      BigDecimal bd = new BigDecimal(Float.toString((float) ((double) ((float) ((1 / mDistance) / sumDistances)))));
      bd = bd.setScale(4, RoundingMode.HALF_UP);

      place.setProbability(bd.floatValue());
      location.getLocation().add(place);
    }
    long timeEnd = System.currentTimeMillis();
    location.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
    return location;
  }

}
