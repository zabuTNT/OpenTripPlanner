package org.opentripplanner.ext.ojp;

import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.opentripplanner.ext.restapi.mapping.FeedScopedIdMapper;
import org.opentripplanner.model.Timetable;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;

import de.vdv.ojp.model.ErrorDescriptionStructure;
import de.vdv.ojp.model.LineRefStructure;
import de.vdv.ojp.model.LocationStructure;
import de.vdv.ojp.model.NaturalLanguageStringStructure;
import de.vdv.ojp.model.OperatorRefStructure;
import de.vdv.ojp.model.ServiceDeliveryErrorConditionStructure;
import de.vdv.ojp.model.StopPointRefStructure;
import de.vdv.ojp.model.VehicleModesOfTransportEnumeration;

import de.vdv.ojp.AbstractResponseContextStructure.Places;
import de.vdv.ojp.CallAtNearStopStructure;
import de.vdv.ojp.CallAtStopStructure;
import de.vdv.ojp.CallAtStopStructure.ServiceArrival;
import de.vdv.ojp.CallAtStopStructure.ServiceDeparture;
import de.vdv.ojp.DatedJourneyStructure;
import de.vdv.ojp.InternationalTextStructure;
import de.vdv.ojp.JourneyRefStructure;
import de.vdv.ojp.ModeStructure;
import de.vdv.ojp.OJPTripInfoDeliveryStructure;
import de.vdv.ojp.OJPTripInfoRequestStructure;
import de.vdv.ojp.ObjectFactory;
import de.vdv.ojp.OperatingDayRefStructure;
import de.vdv.ojp.PlaceStructure;
import de.vdv.ojp.StopEventTypeEnumeration;
import de.vdv.ojp.StopPointStructure;
import de.vdv.ojp.TripInfoResponseContextStructure;
import de.vdv.ojp.TripInfoResultStructure;

public class OJPTripInfoFactory {

  private final OJPTripInfoRequestStructure request;
  private final OtpServerRequestContext serverRequestContext;
  private List<VehicleModesOfTransportEnumeration> filteredMode = new ArrayList<>();
  private List<String> filteredLines = new ArrayList<>();
  private List<String> filteredOperators = new ArrayList<>();

  private boolean includeCalls = false;
  private boolean includeService = false;
  private boolean includeDays = false;
  private boolean excludeModeType = false;
  private boolean excludeLines = false;
  private boolean excludeOperators = false;
  long maxResults = Integer.MAX_VALUE;
  StopEventTypeEnumeration stopEventType = StopEventTypeEnumeration.BOTH;
  private ObjectFactory factory;

  public OJPTripInfoFactory(OtpServerRequestContext serverRequestContext, OJPTripInfoRequestStructure request, ObjectFactory factory) {
    this.serverRequestContext = serverRequestContext;
    this.request = request;
    this.factory = factory;
  }

  public OJPTripInfoDeliveryStructure create() {
    OJPTripInfoDeliveryStructure event = new OJPTripInfoDeliveryStructure();

    long timeStart = System.currentTimeMillis();

    String tripId = null;
    filteredMode = new ArrayList<>();


    Duration duration = Duration.ofHours(24);
    Date dateTmp = new Date();
    ZonedDateTime date = ZonedDateTime.now();

    if (request.getJourneyRef() != null) {
      tripId = request.getJourneyRef().getValue();
    }

    String pattern = "yyyy-MM-dd";
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

    if (request.getOperatingDayRef() != null) {
      String dateString = request.getOperatingDayRef().getValue();
      try {
        dateTmp = simpleDateFormat.parse(dateString);
      } catch (ParseException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      date = dateTmp.toInstant()
        .atZone(ZoneId.systemDefault());
    }

    if (request.getParams() != null) {

      if (request.getParams().isIncludeCalls() != null) {
        includeCalls = request.getParams().isIncludeCalls().booleanValue();
      }

      if (request.getParams().isIncludeService() != null) {
        includeService = request.getParams().isIncludeService().booleanValue();
      }

    }


    if (tripId == null) {
      event.setStatus(false);
      ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
      ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
      descr.setValue("No Trip defined");
      event.setErrorCondition(error.withDescription(descr));
      long timeEnd = System.currentTimeMillis();
      event.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
      return event;
    }
    //search by id
    Trip trip;

    try {
      trip = trip(tripId);
    } catch (NotFoundException e) {
      event.setStatus(false);
      ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
      ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
      descr.setValue("No Trip");
      event.setErrorCondition(error.withDescription(descr));
      long timeEnd = System.currentTimeMillis();
      event.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
      return event;
    }

    Collection<Agency> agencies = transitService()
      .getAgencies()
      .stream()
      .filter(agency -> agency.getId().getFeedId().equals(trip.getId().getFeedId()))
      .toList();

    ZoneId zoneId = agencies.iterator().next().getTimezone();
    ZoneOffset zoneOffSet = zoneId.getRules().getOffset(date.toInstant());

    String lang = agencies.iterator().next().getLang();
    assert lang != null;
    Locale locale = new Locale(lang);


    TripPattern tp = transitService().getPatternForTrip(trip, date.toLocalDate());

    Timetable table = transitService()
      .getTimetableForTripPattern(tp, date.toLocalDate());
    List<TripTimeOnDate> stoptimes = TripTimeOnDate.fromTripTimes(table, trip);

    TripInfoResponseContextStructure context = new TripInfoResponseContextStructure();
    Places places = new Places();
    TripInfoResultStructure result = new TripInfoResultStructure();

    LocalDateTime now = LocalDateTime.now();

    stoptimes.stream()
      .sorted(Comparator.comparing(TripTimeOnDate::getStopIndex))
      .forEach(st -> {
        StopLocation stop = st.getStop();

        PlaceStructure p = new PlaceStructure();
        StopPointStructure sp = new StopPointStructure();
        InternationalTextStructure it = new InternationalTextStructure();
        NaturalLanguageStringStructure nL = new NaturalLanguageStringStructure();
        nL.setLang(lang);
        nL.setValue(stop.getName().toString(locale));
        it.setText(nL);
        sp.setStopPointName(it);
        sp.setWheelchairAccessible(stop.getWheelchairAccessibility() == Accessibility.POSSIBLE);
        LocationStructure geo = new LocationStructure();
        geo.setLatitude(BigDecimal.valueOf(stop.getLat()));
        geo.setLongitude(BigDecimal.valueOf(stop.getLon()));
        p.setGeoPosition(geo);
        StopPointRefStructure sRef = new StopPointRefStructure();
        sRef.setValue(stop.getId().toString());
        sp.setStopPointRef(sRef);
        p.setStopPoint(sp);
        p.setLocationName(it);

        boolean found = false;
        for (PlaceStructure place : places.getLocation()) {
          if (place.getStopPoint().getStopPointRef().equals(sRef)) {
            found = true;
            break;
          }
        }

        if (!found) {
          places.getLocation().add(p);
        }

        if (includeCalls) {
          CallAtStopStructure mCallAtStop = new CallAtStopStructure();

          mCallAtStop.setOrder(BigInteger.valueOf(st.getGtfsSequence()));
          mCallAtStop.setStopPointRef(new StopPointRefStructure().withValue(stop.getId().toString()));


          mCallAtStop.setStopPointName(it);

          ServiceArrival arrival = new ServiceArrival();


          arrival.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeArrival(), 0, zoneOffSet), zoneId));
          arrival.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getScheduledArrival(), 0, zoneOffSet), zoneId));


          mCallAtStop.setServiceArrival(arrival);


          ServiceDeparture departure = new ServiceDeparture();
          departure.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeDeparture(), 0, zoneOffSet), zoneId));
          departure.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getScheduledDeparture(), 0, zoneOffSet), zoneId));


          mCallAtStop.setServiceDeparture(departure);

          if (now.isBefore(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeArrival(), 0, zoneOffSet))) {
            result.getOnwardCall().add(mCallAtStop);
          } else {
            result.getPreviousCall().add(mCallAtStop);
          }

        }


      });

    context.setPlaces(places);
    event.setTripInfoResponseContext(context);
    event.setTripInfoResult(result);

    if (includeService) {
      DatedJourneyStructure js = new DatedJourneyStructure();

      OperatingDayRefStructure op = new OperatingDayRefStructure();

      op.setValue(simpleDateFormat.format(dateTmp));
      //op.setValue(graphIndex.tripForId.get(tts.tripId).getServiceId().toString());

      js.getContent().add(factory.createOperatingDayRef(op));

      JourneyRefStructure jr = new JourneyRefStructure();
      jr.setValue(tripId);

      js.getContent().add(factory.createJourneyRef(jr));

      LineRefStructure line = new LineRefStructure();
      Route route = trip.getRoute();
      line.setValue(
        route.getId().toString());

      js.getContent().add(factory.createDatedJourneyStructureLineRef(line));

      ModeStructure mode = new ModeStructure();
      mode.setPtMode(OJPCommon.getTraverseMode(route));

      js.getContent().add(factory.createDatedJourneyStructureMode(mode));

      OperatorRefStructure operator = new OperatorRefStructure();

      operator.setValue(route.getAgency().getId().toString());

      js.getContent().add(factory.createOperatorRef(operator));

      StopPointRefStructure origin = new StopPointRefStructure();
      StopPointRefStructure destination = new StopPointRefStructure();
      origin.setValue(tp.firstStop().getId().toString());
      destination.setValue(tp.lastStop().getId().toString());


      js.getContent().add(factory.createDatedJourneyStructureOriginStopPointRef(origin));
      js.getContent().add(factory.createDatedJourneyStructureDestinationStopPointRef(destination));

      InternationalTextStructure lineName = new InternationalTextStructure();
      NaturalLanguageStringStructure valueLineLang = new NaturalLanguageStringStructure();

      valueLineLang.setLang(lang);
      valueLineLang.setValue(route.getLongName() != null ? route.getLongName().toString(locale) : route.getShortName());
      lineName.setText(valueLineLang);

      js.getContent().add(factory.createDatedJourneyStructurePublishedLineName(lineName));

      InternationalTextStructure originName = new InternationalTextStructure();
      NaturalLanguageStringStructure valueOriginLang = new NaturalLanguageStringStructure();

      valueOriginLang.setLang(lang);
      valueOriginLang.setValue(tp.firstStop().getName().toString(locale));
      originName.setText(valueOriginLang);


      js.getContent().add(factory.createDatedJourneyStructureOriginText(originName));

      InternationalTextStructure destinationName = new InternationalTextStructure();
      NaturalLanguageStringStructure valueDestinationLang = new NaturalLanguageStringStructure();

      valueDestinationLang.setLang(lang);
      valueDestinationLang.setValue(tp.lastStop().getName().toString(locale));
      destinationName.setText(valueDestinationLang);

      js.getContent().add(factory.createDatedJourneyStructureDestinationText(destinationName));


      result.setService(js);
    }


    long timeEnd = System.currentTimeMillis();
    event.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
    return event;
  }

  private TransitService transitService() {
    return this.serverRequestContext.transitService();
  }

  private static FeedScopedId createId(String name, String value) {
    return FeedScopedIdMapper.mapToDomain(name, value);
  }

  private Trip trip(String tripId) {
    var trip = transitService().getTripForId(createId("tripId", tripId));
    return OJPCommon.validateExist("Trip", trip, "tripId", tripId);
  }


}
