package org.opentripplanner.ext.ojp;

import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opentripplanner.ext.restapi.mapping.FeedScopedIdMapper;
import org.opentripplanner.model.StopTimesInPattern;
import org.opentripplanner.model.Timetable;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.routing.graphfinder.GraphFinder;
import org.opentripplanner.routing.stoptimes.ArrivalDeparture;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
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
import de.vdv.ojp.OJPStopEventDeliveryStructure;
import de.vdv.ojp.OJPStopEventRequestStructure;
import de.vdv.ojp.ObjectFactory;
import de.vdv.ojp.OperatingDayRefStructure;
import de.vdv.ojp.PlaceStructure;
import de.vdv.ojp.StopEventResponseContextStructure;
import de.vdv.ojp.StopEventResultStructure;
import de.vdv.ojp.StopEventStructure;
import de.vdv.ojp.StopEventTypeEnumeration;
import de.vdv.ojp.StopPointStructure;

public class OJPStopEventsFactory {
	
	private final OJPStopEventRequestStructure request;
	private final OtpServerRequestContext serverRequestContext;
	private List<VehicleModesOfTransportEnumeration> filteredMode = new ArrayList<>();
	private List<String> filteredLines = new ArrayList<>();
	private List<String> filteredOperators = new ArrayList<>();
	
	private boolean includePreviousCalls = false;
	private boolean includeOnwardCalls = false;
	private boolean includeRealtime = false;		
	private boolean includeDays = false;
	private boolean excludeModeType = false;
	private boolean excludeLines = false;
	private boolean excludeOperators = false;
	long maxResults = Integer.MAX_VALUE;
	StopEventTypeEnumeration stopEventType = StopEventTypeEnumeration.BOTH;
	private ObjectFactory factory;
	
	public OJPStopEventsFactory(OtpServerRequestContext serverRequestContext, OJPStopEventRequestStructure request, ObjectFactory factory) {
		this.serverRequestContext = serverRequestContext;
		this.request = request;
		this.factory = factory;
	}

	public OJPStopEventDeliveryStructure create() {
		OJPStopEventDeliveryStructure event = new OJPStopEventDeliveryStructure();
		
		long timeStart = System.currentTimeMillis();
		
		String stopCode = null;
		filteredMode = new ArrayList<>();
		
		
		Duration duration = Duration.ofHours(24);
		ZonedDateTime date = ZonedDateTime.now();
		
		if(request.getLocation() != null && request.getLocation().getPlaceRef() != null) {
			if(request.getLocation().getPlaceRef().getStopPointRef() != null) {
				stopCode = request.getLocation().getPlaceRef().getStopPointRef().getValue();
			} else if(request.getLocation().getPlaceRef().getStopPlaceRef() != null) {
				stopCode = request.getLocation().getPlaceRef().getStopPlaceRef().getValue();
			}	
		}
		
		if(request.getLocation().getDepArrTime() != null) {
			date = request.getLocation().getDepArrTime();
		}
		
		if(request.getParams() != null) {
									
			if(request.getParams().getPtModeFilter() != null) {
				filteredMode = request.getParams().getPtModeFilter().getPtMode();
				excludeModeType = request.getParams().getPtModeFilter().isExclude() != null ? request.getParams().getPtModeFilter().isExclude() : false; 
			}
			
			if(request.getParams().getLineFilter() != null) {
				filteredLines = request.getParams().getLineFilter().getLine().stream().map(l -> l.getLineRef().getValue()).collect(Collectors.toList());
				excludeLines = request.getParams().getLineFilter().isExclude() != null ? request.getParams().getLineFilter().isExclude() : false;
			}
			
			if(request.getParams().getOperatorFilter() != null) {
				filteredOperators = request.getParams().getOperatorFilter().getOperatorRef().stream().map(o -> o.getValue()).collect(Collectors.toList());
				excludeOperators = request.getParams().getOperatorFilter().isExclude() != null ? request.getParams().getLineFilter().isExclude() : false;
			}
			
			
			if(request.getParams().isIncludePreviousCalls() != null) {
				includePreviousCalls = request.getParams().isIncludePreviousCalls();
			}
			
			if(request.getParams().isIncludeOnwardCalls() != null) {
				includeOnwardCalls = request.getParams().isIncludeOnwardCalls();
			}
			
			if(request.getParams().isIncludeRealtimeData() != null) {
				includeRealtime = request.getParams().isIncludeRealtimeData();
			}
			
			if(request.getParams().isIncludeOperatingDays() != null) {
				includeDays = request.getParams().isIncludeOperatingDays();
			}
			
			if(request.getParams().getStopEventType() != null) {
				stopEventType = request.getParams().getStopEventType();
			}
						
			if(request.getParams().getNumberOfResults() != null) {
				maxResults = request.getParams().getNumberOfResults().longValue();
			}
			
			if(request.getParams().getTimeWindow() != null) {
				duration = request.getParams().getTimeWindow();
			}
			
		}
		

		
		if(stopCode == null) {
			event.setStatus(false);
			ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
			ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
			descr.setValue("No Location defined");
			event.setErrorCondition(error.withDescription(descr ));
			long timeEnd = System.currentTimeMillis();
			event.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
			return event;		
		}
			//search by id
		StopLocation stop;
		try{
			 stop = stop(stopCode);
		}catch(NotFoundException e){
			event.setStatus(false);
			ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
			ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
			descr.setValue("No Location");
			event.setErrorCondition(error.withDescription(descr ));
			long timeEnd = System.currentTimeMillis();
			event.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
			return event;
		}


		Collection<Agency> agencies = transitService()
			.getAgencies()
			.stream()
			.filter(agency -> agency.getId().getFeedId().equals(stop.getId().getFeedId()))
			.toList();

		ZoneId zoneId = agencies.iterator().next().getTimezone();
		ZoneOffset zoneOffSet = zoneId.getRules().getOffset(date.toInstant());

		String lang = agencies.iterator().next().getLang();
		Locale locale = new Locale(lang);

		List<StopTimesInPattern> stoptimes = transitService().stopTimesForStop(
			stop,
			date.toInstant(),
			duration,
			(int)200000,
			ArrivalDeparture.BOTH,
			false
			);

		stoptimes.stream()
	        .flatMap(stoptimesWithPattern -> stoptimesWithPattern.times.stream())
	        .filter(st -> {
	        	Route route = st.getTrip().getRoute();
	        	if(!filteredLines.isEmpty()) {
	        		if(excludeLines) {
	        			if(filteredLines.contains(route.getId().toString())) {
	        				return false;
	        			}
	        		}else {
	        			if(!filteredLines.contains(route.getId().toString())) {
	        				return false;
	        			}
	        				
	        		}
	        	}
	        	
	        	if(!filteredMode.isEmpty()) {
	        		if(excludeModeType) {
	        			if(filteredMode.contains(OJPCommon.getTraverseMode(route))) {
	        				return false;
	        			}
	        		}else {
	        			if(!filteredMode.contains(OJPCommon.getTraverseMode(route))) {
	        				return false;
	        			}
	        		}
	        	}
	        	
	        	if(!filteredOperators.isEmpty()) {
	        		if(excludeOperators) {
	        			if(filteredOperators.contains(route.getAgency().getId().toString())) {
	        				return false;
	        			}
	        		}else {
	        			if(!filteredOperators.contains(route.getAgency().getId().toString())) {
	        				return false;
	        			}
	        		}
	        	}
	        	
	        	return true;
	        })
	        .sorted(Comparator.comparing(t -> t.getServiceDayMidnight() + t.getRealtimeDeparture()))
	        .limit(maxResults)
	        .collect(Collectors.toList());
		StopEventResponseContextStructure context = new StopEventResponseContextStructure();
		
		Places places = new Places();
		PlaceStructure p = new PlaceStructure();
		StopPointStructure sp = new StopPointStructure();
		InternationalTextStructure it = new InternationalTextStructure();
		NaturalLanguageStringStructure nL = new NaturalLanguageStringStructure();

		nL.setLang(lang);
		nL.setValue(Objects.requireNonNull(stop.getName()).toString(locale));
		it.setText(nL);
		sp.setStopPointName(it);
		sp.setWheelchairAccessible(stop.getWheelchairAccessibility() == Accessibility.POSSIBLE);
		LocationStructure geo = new LocationStructure();
		geo.setLatitude(BigDecimal.valueOf(stop.getLat()));
		geo.setLongitude(BigDecimal.valueOf(stop.getLon()));
		p.setGeoPosition(geo);
		StopPointRefStructure sRef = new StopPointRefStructure();
		sRef.setValue(stop.getId().toString());
		sp.setStopPointRef(sRef );
		p.setStopPoint(sp);
		p.setLocationName(it);
		
		
		places.getLocation().add(p);
		context.setPlaces(places );
		for(StopTimesInPattern stoptime: stoptimes) {
			for(TripTimeOnDate tts : stoptime.times) {


				StopEventResultStructure stopEvent = new StopEventResultStructure();
				TripIdAndServiceDate tsd = new TripIdAndServiceDate(tts.getTrip().getId(), tts.getServiceDay());

				TripPattern tp = transitService().getPatternForTrip(tts.getTrip(),tts.getServiceDay());

				Timetable table = transitService()
					.getTimetableForTripPattern(tp, tts.getServiceDay());
				List<TripTimeOnDate> tripTimesOnDate = TripTimeOnDate.fromTripTimes(table, tts.getTrip());

				stopEvent.setResultId(tts.getTrip().getId().toString() + ":" + stop.getId().toString());
				StopEventStructure s = new StopEventStructure();
				CallAtNearStopStructure c = new CallAtNearStopStructure();
				
				CallAtStopStructure callAtStop = new CallAtStopStructure();
				callAtStop.setOrder(BigInteger.valueOf(tts.getGtfsSequence()));
				callAtStop.setStopPointRef(new StopPointRefStructure().withValue(stop.getId().toString()));
				
				DatedJourneyStructure js = new DatedJourneyStructure();
				
				OperatingDayRefStructure op = new OperatingDayRefStructure();
				DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				op.setValue(tts.getServiceDay().format(dateTimeFormatter));

				js.getContent().add(factory.createOperatingDayRef(op));
				
				JourneyRefStructure jr = new JourneyRefStructure();
				jr.setValue(tts.getTrip().getId().toString());
				
				js.getContent().add(factory.createJourneyRef(jr));
				
				LineRefStructure line = new LineRefStructure();
				Route route = tts.getTrip().getRoute();
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
				valueOriginLang.setValue(Objects.requireNonNull(tp.firstStop().getName()).toString(locale));
				originName.setText(valueOriginLang);
				
				js.getContent().add(factory.createDatedJourneyStructureOriginText(originName));
				
				InternationalTextStructure destinationName = new InternationalTextStructure();
				NaturalLanguageStringStructure valueDestinationLang = new NaturalLanguageStringStructure();
				
				valueDestinationLang.setLang(lang);
				valueDestinationLang.setValue(Objects.requireNonNull(tp.lastStop().getName()).toString(locale));
				destinationName.setText(valueDestinationLang);
				
				js.getContent().add(factory.createDatedJourneyStructureDestinationText(destinationName));	
				
				
				s.setService(js);
				
				InternationalTextStructure itS = new InternationalTextStructure();
				NaturalLanguageStringStructure nLS = new NaturalLanguageStringStructure();
				nLS.setLang(lang);
				nLS.setValue(stop.getName().toString(locale));
				itS.setText(nLS);
				
				callAtStop.setStopPointName(it);
				
				if(stopEventType.equals(StopEventTypeEnumeration.BOTH) || stopEventType.equals(StopEventTypeEnumeration.ARRIVAL)) {
					ServiceArrival arrival = new ServiceArrival();
					if(includeRealtime) {
						arrival.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(tts.getServiceDayMidnight() + tts.getRealtimeArrival(), 0, zoneOffSet),zoneId));
					}
					
					arrival.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(tts.getServiceDayMidnight() + tts.getScheduledArrival(), 0, zoneOffSet),zoneId));
					
					
					callAtStop.setServiceArrival(arrival);
				}
				
				if(stopEventType.equals(StopEventTypeEnumeration.BOTH) || stopEventType.equals(StopEventTypeEnumeration.DEPARTURE)) {
					ServiceDeparture departure = new ServiceDeparture();
					if(includeRealtime) {
						departure.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(tts.getServiceDayMidnight() + tts.getRealtimeDeparture(), 0, zoneOffSet),zoneId));
					}
					
					departure.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(tts.getServiceDayMidnight() + tts.getScheduledDeparture(), 0, zoneOffSet),zoneId));
									
					
					callAtStop.setServiceDeparture(departure);
					
				}

				c.setCallAtStop(callAtStop);
				s.setThisCall(c);
				
				if(includeOnwardCalls) {

					tripTimesOnDate.stream().filter(st -> st.getGtfsSequence() > tts.getGtfsSequence()).forEach(st -> {
						CallAtNearStopStructure mCall = new CallAtNearStopStructure();
						CallAtStopStructure mCallAtStop = new CallAtStopStructure();

						StopLocation stopTmp = st.getStop();
						
						PlaceStructure pTmp = new PlaceStructure();
						StopPointStructure spTmp = new StopPointStructure();
						
						InternationalTextStructure itTmp = new InternationalTextStructure();
						NaturalLanguageStringStructure nLTmp = new NaturalLanguageStringStructure();
						nLTmp.setLang(lang);
						nLTmp.setValue(Objects.requireNonNull(stopTmp.getName()).toString(locale));
						itTmp.setText(nLTmp);
						
						
						spTmp.setStopPointName(itTmp);
						spTmp.setWheelchairAccessible(stopTmp.getWheelchairAccessibility() == Accessibility.POSSIBLE);
						LocationStructure geoTmp = new LocationStructure();
						geoTmp.setLatitude(BigDecimal.valueOf(stopTmp.getLat()));
						geoTmp.setLongitude(BigDecimal.valueOf(stopTmp.getLon()));
						pTmp.setGeoPosition(geoTmp);
						StopPointRefStructure sRefTmp = new StopPointRefStructure();
						sRefTmp.setValue(stopTmp.getId().toString());
						spTmp.setStopPointRef(sRefTmp );
						pTmp.setStopPoint(spTmp);
						pTmp.setLocationName(itTmp);
						
						boolean found = false;
						for(PlaceStructure singlePlace : places.getLocation()) {
							if(singlePlace.getStopPoint().getStopPointRef().getValue().equals(pTmp.getStopPoint().getStopPointRef().getValue())) {
								found = true;
								break;
							}
						}
						if(!found)
							places.getLocation().add(pTmp);
						
						
						mCallAtStop.setOrder(BigInteger.valueOf(st.getGtfsSequence()));
						mCallAtStop.setStopPointRef(new StopPointRefStructure().withValue(stopTmp.getId().toString()));
						
												
						mCallAtStop.setStopPointName(itTmp);
						
						if(stopEventType.equals(StopEventTypeEnumeration.BOTH) || stopEventType.equals(StopEventTypeEnumeration.ARRIVAL)) {
							ServiceArrival arrival = new ServiceArrival();
							if(includeRealtime) {
								arrival.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeArrival(), 0, zoneOffSet),zoneId));
							}
							
							arrival.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getScheduledArrival(), 0, zoneOffSet),zoneId));
							
							
							mCallAtStop.setServiceArrival(arrival);
						}
						
						if(stopEventType.equals(StopEventTypeEnumeration.BOTH) || stopEventType.equals(StopEventTypeEnumeration.DEPARTURE)) {
							ServiceDeparture departure = new ServiceDeparture();
							if(includeRealtime) {
								departure.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeDeparture(), 0, zoneOffSet),zoneId));
							}
							
							departure.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getScheduledDeparture(), 0, zoneOffSet),zoneId));
											
							
							mCallAtStop.setServiceDeparture(departure);
							
						}
																		
						mCall.setCallAtStop(mCallAtStop );
						s.getOnwardCall().add(mCall );
					});
				}
				
				if(includePreviousCalls) {
					tripTimesOnDate.stream().filter(st -> st.getGtfsSequence() < tts.getGtfsSequence()).forEach(st -> {
						CallAtNearStopStructure mCall = new CallAtNearStopStructure();
						CallAtStopStructure mCallAtStop = new CallAtStopStructure();
						
						StopLocation stopTmp = st.getStop();
						
						PlaceStructure pTmp = new PlaceStructure();
						StopPointStructure spTmp = new StopPointStructure();
						
						InternationalTextStructure itTmp = new InternationalTextStructure();
						NaturalLanguageStringStructure nLTmp = new NaturalLanguageStringStructure();
						nLTmp.setLang(lang);
						nLTmp.setValue(Objects.requireNonNull(stopTmp.getName()).toString(locale));
						itTmp.setText(nLTmp);
						
						
						spTmp.setStopPointName(itTmp);
						spTmp.setWheelchairAccessible(stopTmp.getWheelchairAccessibility() == Accessibility.POSSIBLE);
						LocationStructure geoTmp = new LocationStructure();
						geoTmp.setLatitude(BigDecimal.valueOf(stopTmp.getLat()));
						geoTmp.setLongitude(BigDecimal.valueOf(stopTmp.getLon()));
						pTmp.setGeoPosition(geoTmp);
						StopPointRefStructure sRefTmp = new StopPointRefStructure();
						sRefTmp.setValue(stopTmp.getId().toString());
						spTmp.setStopPointRef(sRefTmp );
						pTmp.setStopPoint(spTmp);
						pTmp.setLocationName(itTmp);
						
						boolean found = false;
						for(PlaceStructure singlePlace : places.getLocation()) {
							if(singlePlace.getStopPoint().getStopPointRef().getValue().equals(pTmp.getStopPoint().getStopPointRef().getValue())) {
								found = true;
								break;
							}
						}
						if(!found)
							places.getLocation().add(pTmp);
						
						
						mCallAtStop.setOrder(BigInteger.valueOf(st.getGtfsSequence()));
						mCallAtStop.setStopPointRef(new StopPointRefStructure().withValue(stopTmp.getId().toString()));
						
												
						mCallAtStop.setStopPointName(itTmp);
						
						if(stopEventType.equals(StopEventTypeEnumeration.BOTH) || stopEventType.equals(StopEventTypeEnumeration.ARRIVAL)) {
							ServiceArrival arrival = new ServiceArrival();
							if(includeRealtime) {
								arrival.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeArrival(), 0, zoneOffSet),zoneId));
							}

							arrival.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getScheduledArrival(), 0, zoneOffSet),zoneId));
							
							
							mCallAtStop.setServiceArrival(arrival);
						}
						
						if(stopEventType.equals(StopEventTypeEnumeration.BOTH) || stopEventType.equals(StopEventTypeEnumeration.DEPARTURE)) {
							ServiceDeparture departure = new ServiceDeparture();
							if(includeRealtime) {
								departure.setEstimatedTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getRealtimeDeparture(), 0, zoneOffSet),zoneId));
							}

							departure.setTimetabledTime(ZonedDateTime.of(LocalDateTime.ofEpochSecond(st.getServiceDayMidnight() + st.getScheduledDeparture(), 0, zoneOffSet),zoneId));
											
							
							mCallAtStop.setServiceDeparture(departure);
							
						}
						
						
						
												
						mCall.setCallAtStop(mCallAtStop );
						s.getPreviousCall().add(mCall );
					});
				}
				
				stopEvent.setStopEvent(s);
				
				event.getStopEventResult().add(stopEvent);
			}
				
		}
		
	
		
		event.setStopEventResponseContext(context);
		
		long timeEnd = System.currentTimeMillis();
		event.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
		return event;
	}

	private TransitService transitService() {
		return this.serverRequestContext.transitService();
	}



	private StopLocation stop(String stopId) {
		var stop = transitService().getRegularStop(OJPCommon.createId("stopId", stopId));
		return OJPCommon.validateExist("Stop", stop, "stopId", stop);
	}
	
}
