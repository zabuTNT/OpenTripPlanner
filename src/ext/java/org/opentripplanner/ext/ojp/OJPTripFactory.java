package org.opentripplanner.ext.ojp;

import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;

import graphql.ExecutionInput;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
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
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.api.common.Message;
import org.opentripplanner.api.resource.DebugOutput;
import org.opentripplanner.apis.support.mapping.PlannerErrorMapper;
import org.opentripplanner.apis.transmodel.TransmodelRequestContext;
import org.opentripplanner.apis.transmodel.mapping.TripRequestMapper;
import org.opentripplanner.ext.restapi.mapping.FeedScopedIdMapper;
import org.opentripplanner.ext.restapi.mapping.TripPlanMapper;
import org.opentripplanner.ext.restapi.mapping.TripSearchMetadataMapper;
import org.opentripplanner.ext.restapi.model.ApiItinerary;
import org.opentripplanner.ext.restapi.model.ApiLeg;
import org.opentripplanner.ext.restapi.model.ApiPlace;
import org.opentripplanner.ext.restapi.model.ApiTripPlan;
import org.opentripplanner.ext.restapi.model.TripPlannerResponse;
import org.opentripplanner.framework.geometry.EncodedPolyline;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.Timetable;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.TripPlan;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.PassThroughPoint;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.TransferPreferences;
import org.opentripplanner.routing.api.request.preference.WheelchairPreferences;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

import de.vdv.ojp.model.ErrorDescriptionStructure;
import de.vdv.ojp.model.LineDirectionStructure;
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
import de.vdv.ojp.DatedJourneyStructure;
import de.vdv.ojp.InternationalTextStructure;
import de.vdv.ojp.JourneyRefStructure;
import de.vdv.ojp.LegAlightStructure;
import de.vdv.ojp.LegBoardStructure;
import de.vdv.ojp.LegIntermediateStructure;
import de.vdv.ojp.LegTrackStructure;
import de.vdv.ojp.ModeStructure;
import de.vdv.ojp.NotViaStructure;
import de.vdv.ojp.OJPStopEventDeliveryStructure;
import de.vdv.ojp.OJPStopEventRequestStructure;
import de.vdv.ojp.OJPTripDeliveryStructure;
import de.vdv.ojp.OJPTripInfoDeliveryStructure;
import de.vdv.ojp.OJPTripInfoRequestStructure;
import de.vdv.ojp.OJPTripRequestStructure;
import de.vdv.ojp.ObjectFactory;
import de.vdv.ojp.OperatingDayRefStructure;
import de.vdv.ojp.PlaceContextStructure;
import de.vdv.ojp.PlaceRefStructure;
import de.vdv.ojp.PlaceStructure;
import de.vdv.ojp.StopEventResponseContextStructure;
import de.vdv.ojp.StopEventResultStructure;
import de.vdv.ojp.StopEventStructure;
import de.vdv.ojp.StopEventTypeEnumeration;
import de.vdv.ojp.StopPointStructure;
import de.vdv.ojp.TimedLegStructure;
import de.vdv.ojp.TrackSectionStructure;
import de.vdv.ojp.TrackSectionStructure.LinkProjection;
import de.vdv.ojp.TransferLegStructure;
import de.vdv.ojp.TransferModesEnumeration;
import de.vdv.ojp.TripInfoResponseContextStructure;
import de.vdv.ojp.TripInfoResultStructure;
import de.vdv.ojp.TripLegStructure;
import de.vdv.ojp.TripResponseContextStructure;
import de.vdv.ojp.TripResultStructure;
import de.vdv.ojp.TripStructure;
import de.vdv.ojp.TripViaStructure;

public class OJPTripFactory {
	
	private final OJPTripRequestStructure request;
	private final OtpServerRequestContext serverRequestContext;
	private List<VehicleModesOfTransportEnumeration> modeList = new ArrayList<>();
	private List<LineDirectionStructure> filteredLines = new ArrayList<>();
	private List<OperatorRefStructure> filteredOperators = new ArrayList<>();
	private boolean excludeModes = false;
	
	
	private List<String> requestModes = new ArrayList<>();
	private List<String> requestBannedLines = new ArrayList<>();
	private List<String> requestWhitelistLines = new ArrayList<>();
	private HashSet<String> requestBannedOperators = new HashSet<String>();
	private HashSet<String> requestWhitelistOperators = new HashSet<String>();
	
	private List<String> requestBannedStops = new ArrayList<>();
	private List<Map<String, Object>> viaStops = new ArrayList<>();
	
	private boolean includeAccessibility = false;
	private boolean includeIntermediateStops = false;		
	private boolean includeTrack = false;
//	private boolean includeProjection = false;
	
	long transferLimit = Integer.MAX_VALUE;
	long maxResults = Integer.MAX_VALUE;
	
	private ObjectFactory factory;
	
	List<String> allOTPModes = Arrays.asList("TRAM","SUBWAY","RAIL","BUS","FERRY","GONDOLA","FUNICULAR");
	

	public OJPTripFactory(OtpServerRequestContext serverRequestContext, OJPTripRequestStructure request, ObjectFactory factory) {
		this.serverRequestContext = serverRequestContext;
		this.request = request;
		this.factory = factory;
	}

	public OJPTripDeliveryStructure create() {
		OJPTripDeliveryStructure trip = new OJPTripDeliveryStructure();
		
		long timeStart = System.currentTimeMillis();
		
		String tripId = null;
		
		
		Duration duration = Duration.ofHours(24);
		Date dateTmp = new Date();
		LocalDateTime date = null;
			
		String pattern = "yyyy-MM-dd";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		
		PlaceContextStructure origin = null;
		String originStop = null;
		String originName = null;
		BigDecimal originLat = null;
		BigDecimal originLng = null;
		
		PlaceContextStructure destination = null;
		String destinationStop = null;
		String destinationName = null;
		BigDecimal destinationLat = null;
		BigDecimal destinationLng = null;
		
		
		ZonedDateTime departureTime = ZonedDateTime.now();
		ZonedDateTime arrivalTime = ZonedDateTime.now();
		
		//requestModes.add("WALK");
		
		if(request.getParams() != null) {
			if(request.getParams().getPtModeFilter() != null) {
				modeList = request.getParams().getPtModeFilter().getPtMode();
				excludeModes = request.getParams().getPtModeFilter().isExclude();
				for(VehicleModesOfTransportEnumeration mode : modeList) {
					String otpMode = OJPCommon.convertOTPModes(mode);
					if(otpMode != null) {
						if(!excludeModes) {
							requestModes.add(otpMode);
						}else {
							allOTPModes.remove(otpMode);
						}
					}
					
				}
				if(excludeModes) {
					requestModes.addAll(allOTPModes);
				}
			}else {
				requestModes.addAll(allOTPModes);
			}
			
			if(request.getParams().getLineFilter() != null) {
				filteredLines = request.getParams().getLineFilter().getLine();
				boolean excludeLines = request.getParams().getLineFilter().isExclude();
				for(LineDirectionStructure l : filteredLines) {
					if(excludeLines) {
						requestBannedLines.add(l.getLineRef().getValue());
					}else {
						requestWhitelistLines.add(l.getLineRef().getValue());
					}
					
				}
			}
			
			if(request.getParams().getOperatorFilter() != null) {
				filteredOperators = request.getParams().getOperatorFilter().getOperatorRef();
				boolean excludeOperators = request.getParams().getOperatorFilter().isExclude();
				for(OperatorRefStructure o : filteredOperators) {
					if(excludeOperators) {
						requestBannedOperators.add(o.getValue());
					}else {
						requestWhitelistOperators.add(o.getValue());
					}
				}
			}
			
			if(request.getParams().getNumberOfResults() != null) {
				maxResults = request.getParams().getNumberOfResults().longValue();
			}
			if(request.getParams().isIncludeTrackSections() != null) {
				includeTrack = request.getParams().isIncludeTrackSections();
			}
			
			if(request.getParams().getTransferLimit() != null) {
				transferLimit = request.getParams().getTransferLimit().longValue();
			}
			
			if(request.getParams().isIncludeIntermediateStops() != null) {
				includeIntermediateStops = request.getParams().isIncludeIntermediateStops();
			}
			
			if(request.getParams().isIncludeAccessibility() != null) {
				includeAccessibility = request.getParams().isIncludeAccessibility();
			}
			
			
		}
		
		
		
		if(request.getOrigin() != null && !request.getOrigin().isEmpty()) {
			origin = request.getOrigin().get(0);
			if(origin.getPlaceRef().getStopPlaceRef() != null) {
				originStop = origin.getPlaceRef().getStopPlaceRef().getValue();
			}else if(origin.getPlaceRef().getStopPointRef() != null) {
				originStop = origin.getPlaceRef().getStopPointRef().getValue();
			}
			
			
			
			if(origin.getPlaceRef().getLocationName() != null) {
				originName = origin.getPlaceRef().getLocationName().getText().getValue();
			}
			
			if(origin.getPlaceRef().getGeoPosition() != null) {
				
				if(origin.getPlaceRef().getGeoPosition().getCoordinates() != null) {
					originLat = BigDecimal.valueOf(Long.parseLong(origin.getPlaceRef().getGeoPosition().getCoordinates().getValue().get(1)));
					originLng = BigDecimal.valueOf(Long.parseLong(origin.getPlaceRef().getGeoPosition().getCoordinates().getValue().get(0)));
				}else {
					originLat = origin.getPlaceRef().getGeoPosition().getLatitude();
					originLng = origin.getPlaceRef().getGeoPosition().getLongitude();
				}
				
				
			}
			
			departureTime = origin.getDepArrTime();
			
		}
		
		if(request.getDestination() != null && !request.getDestination().isEmpty()) {
			destination = request.getDestination().get(0);
			if(destination.getPlaceRef().getStopPlaceRef() != null) {
				destinationStop = destination.getPlaceRef().getStopPlaceRef().getValue();
			}else if(destination.getPlaceRef().getStopPointRef() != null) {
				destinationStop = destination.getPlaceRef().getStopPointRef().getValue();
			}
			
			if(destination.getPlaceRef().getLocationName() != null) {
				destinationName = destination.getPlaceRef().getLocationName().getText().getValue();
			}
			if(destination.getPlaceRef().getGeoPosition() != null) {
				
				if(destination.getPlaceRef().getGeoPosition().getCoordinates() != null) {
					destinationLat = BigDecimal.valueOf(Long.parseLong(destination.getPlaceRef().getGeoPosition().getCoordinates().getValue().get(1)));
					destinationLng = BigDecimal.valueOf(Long.parseLong(destination.getPlaceRef().getGeoPosition().getCoordinates().getValue().get(0)));
				}else {
					destinationLat = destination.getPlaceRef().getGeoPosition().getLatitude();
					destinationLng = destination.getPlaceRef().getGeoPosition().getLongitude();
				}
			}		
			
			arrivalTime = destination.getDepArrTime();
			
			
		}
		
		if(request.getVia() != null) {
			Map<String,Object> map = new HashMap<String,Object>();
			map.put("name", "viapoints");
			List<String> stopsVia = new ArrayList<String>();
			for(TripViaStructure viapoint : request.getVia()) {
				String viapointStop = null;
				String viapointStopName = "";
				BigDecimal viapointStopLat = null;
				BigDecimal viapointStopLng = null;
				
				if(viapoint.getViaPoint().getStopPlaceRef() != null) {
					viapointStop = viapoint.getViaPoint().getStopPlaceRef().getValue();
				}else if(viapoint.getViaPoint().getStopPointRef() != null) {
					viapointStop = viapoint.getViaPoint().getStopPointRef().getValue();
				}
				
				if(viapoint.getViaPoint().getLocationName() != null) {
					viapointStopName = viapoint.getViaPoint().getLocationName().getText().getValue();
				}
				if(viapoint.getViaPoint().getGeoPosition() != null) {
					
					if(viapoint.getViaPoint().getGeoPosition().getCoordinates() != null) {
						viapointStopLat = BigDecimal.valueOf(Long.valueOf(viapoint.getViaPoint().getGeoPosition().getCoordinates().getValue().get(1)));
						viapointStopLng = BigDecimal.valueOf(Long.valueOf(viapoint.getViaPoint().getGeoPosition().getCoordinates().getValue().get(0)));
					}else {
						viapointStopLat = viapoint.getViaPoint().getGeoPosition().getLatitude();
						viapointStopLng = viapoint.getViaPoint().getGeoPosition().getLongitude();
					}
				}

				if(viapointStop != null) {
					stopsVia.add(viapointStop);
				}else if(viapointStopLat != null && viapointStopLng != null){

					List<NearbyStop> nearestStops = serverRequestContext.graphFinder().findClosestStops(new Coordinate(viapointStopLng.doubleValue(),viapointStopLat.doubleValue()), 100);
					for(NearbyStop ns : nearestStops){
						stopsVia.add(ns.stop.getId().toString());
					}
				}
			}
			map.put("placeIds", stopsVia);
			viaStops.add(map);
		}
		
		if(request.getNotVia() != null) {
			for(NotViaStructure notviapoint : request.getNotVia()) {
				if(notviapoint.getStopPlaceRef()!=null) {
					requestBannedStops.add(notviapoint.getStopPlaceRef().getValue());
				}else if(notviapoint.getStopPointRef()!= null) {
					requestBannedStops.add(notviapoint.getStopPointRef().getValue());
				}
			}
			
		}
		
		if(originStop == null && (originLat == null || originLng == null)) {
			trip.setStatus(false);
			ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
			ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
			descr.setValue("No Origin Defined");
			trip.setErrorCondition(error.withDescription(descr ));
			long timeEnd = System.currentTimeMillis();
			trip.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
			return trip;
		}
		
		if(destinationStop == null && (destinationLat == null || destinationLng == null)) {
			trip.setStatus(false);
			ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
			ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
			descr.setValue("No Destination Defined");
			trip.setErrorCondition(error.withDescription(descr ));
			long timeEnd = System.currentTimeMillis();
			trip.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
			return trip;
		}
		
		Map<String,Object> requestMap = new HashMap<String,Object>();
		
		if(originStop != null) {
			String[] originSplitted = originStop.split("_");
			requestMap.put("to", GenericLocation.fromStopId("", originSplitted[0], originSplitted[1]));
		}
		else {
			requestMap.put("from", toGenericLocation(originLng.doubleValue(), originLat.doubleValue()));
		}
		if(destinationStop != null) {
			String[] destinationSplitted = destinationStop.split("_");
			requestMap.put("to", GenericLocation.fromStopId("", destinationSplitted[0], destinationSplitted[1]));
		}else {
			requestMap.put("from", toGenericLocation(destinationLng.doubleValue(), destinationLat.doubleValue()));
		}

		if(departureTime != null){
			requestMap.put("dateTime", departureTime);
		}else{
			requestMap.put("dateTime", arrivalTime);
			requestMap.put("arriveBy", true);
		}


		requestMap.put("numTripPatterns", maxResults);
		requestMap.put("maximumTransfers", transferLimit);
		//requestMap.put("includeAccessibility", includeAccessibility);
		requestMap.put("includeIntermediateStops", includeIntermediateStops);

		Map<String,Object> inputModes = new HashMap<>();
		inputModes.put("transportMode", requestModes);


		requestMap.put("modes", inputModes);
		
		if(!requestBannedStops.isEmpty()) {
			requestMap.put("bannedStops", requestBannedStops);
		}

		if(!viaStops.isEmpty()) {
			requestMap.put("passThroughPoints", viaStops);
		}

		Map<String,Object> whiteListed = new HashMap<>();
		Map<String,Object> banned = new HashMap<>();

		if(!requestWhitelistLines.isEmpty()) {
			whiteListed.put("lines", requestWhitelistLines);
		}

		if(!requestWhitelistOperators.isEmpty()) {
			whiteListed.put("authorities", requestWhitelistOperators);
		}

		if(!requestBannedLines.isEmpty()) {
			requestMap.put("lines", requestBannedLines);
		}
		
		if(!requestBannedOperators.isEmpty()) {
			requestMap.put("authorities", requestBannedOperators);
		}

		requestMap.put("whiteListed", whiteListed);
		requestMap.put("banned", banned);

		RouteRequest request = createRequest(requestMap);

		List<Message> messages = new ArrayList<>();
		try {

			RoutingResponse res = serverRequestContext.routingService().route(request);

			TripPlanMapper tripPlanMapper = new TripPlanMapper(request.locale(), includeIntermediateStops);
			TripPlannerResponse response = new TripPlannerResponse(null);
			response.setPlan(tripPlanMapper.mapTripPlan(res.getTripPlan()));
			if (res.getPreviousPageCursor() != null) {
				response.setPreviousPageCursor(res.getPreviousPageCursor().encode());
			}
			if (res.getNextPageCursor() != null) {
				response.setNextPageCursor(res.getNextPageCursor().encode());
			}
			response.setMetadata(TripSearchMetadataMapper.mapTripSearchMetadata(res.getMetadata()));
			if (!res.getRoutingErrors().isEmpty()) {
				// The api can only return one error message, so the first one is mapped
				response.setError(PlannerErrorMapper.mapMessage(res.getRoutingErrors().get(0)));
			}


			ApiTripPlan plan = response.getPlan();

			String lang = "en";
			ZoneId zone = ZoneId.of("UTC");

			trip.setStatus(true);
			Places allPlaces = new Places();
			HashSet<PlaceStructure> allMyPlaces = new HashSet<PlaceStructure>();
			for(ApiItinerary itinerary : plan.itineraries) {
				TripResultStructure tripResult = new TripResultStructure();
				long tripDistance = 0;
				tripResult.setResultId(randomUUID());
				TripStructure tripStructure = new TripStructure();
				tripStructure.setTripId(randomUUID());
				tripStructure.setDuration(Duration.ofSeconds(itinerary.duration));
				tripStructure.setStartTime(toLocalDateTime(itinerary.startTime).atZone(ZoneId.of("UTC")));
				tripStructure.setEndTime(toLocalDateTime(itinerary.endTime).atZone(ZoneId.of("UTC")));
				tripStructure.setTransfers(BigInteger.valueOf(itinerary.transfers));
				for(ApiLeg leg : itinerary.legs) {
					TripLegStructure legStructure = new TripLegStructure();
					tripDistance += leg.distance;
					if(!leg.transitLeg) {
						TransferLegStructure transferLeg = new TransferLegStructure();
						if(leg.mode.equals("WALK")) {
							transferLeg.setTransferMode(TransferModesEnumeration.WALK);
						} //TODO: fix this if when BICYCLES/MICROMOBILITIES will be allowed in the request

						PlaceRefStructure start = new PlaceRefStructure();
						PlaceRefStructure end = new PlaceRefStructure();

						ApiPlace from = leg.from;
						ApiPlace to = leg.to;


						if(from.stopId != null) {
							start.setStopPointRef(new StopPointRefStructure().withValue(from.stopId.toString()));
							allMyPlaces.add(
								new PlaceStructure()
								.withLocationName(getInternationName(from.name,"en"))
								.withStopPoint(new StopPointStructure()
										.withStopPointRef(
												new StopPointRefStructure().withValue(from.stopId.toString())
												)
										.withStopPointName(getInternationName(from.name,"en"))
										)
									.withGeoPosition(
											new LocationStructure()
											.withLatitude(BigDecimal.valueOf(from.lat))
											.withLongitude(BigDecimal.valueOf(from.lon))));
						}else {
							start.setGeoPosition(new LocationStructure().withLatitude(BigDecimal.valueOf(from.lat)).withLongitude(BigDecimal.valueOf(from.lon)));
						}

						start.setLocationName(getInternationName(from.name,"en") );

						if(to.stopId != null) {
							end.setStopPointRef(new StopPointRefStructure().withValue(to.stopId));
							allMyPlaces.add(
									new PlaceStructure()
									.withLocationName(getInternationName(to.name,lang))
									.withStopPoint(new StopPointStructure()
											.withStopPointRef(
													new StopPointRefStructure().withValue(to.stopId)
													)
											.withStopPointName(getInternationName(to.name,lang))
											)
									.withGeoPosition(
											new LocationStructure()
											.withLatitude(BigDecimal.valueOf(to.lat))
											.withLongitude(BigDecimal.valueOf(to.lon))));
						} else {
							end.setGeoPosition(new LocationStructure().withLatitude(BigDecimal.valueOf(to.lat)).withLongitude(BigDecimal.valueOf(to.lon)));
						}

						end.setLocationName(getInternationName(to.name,lang) );
                		
						transferLeg.setLegStart(start);
						transferLeg.setLegEnd(end );
						
						
						transferLeg.setDuration(Duration.ofSeconds((long) leg.getDuration()));
						transferLeg.setWalkDuration(Duration.ofSeconds((long) leg.getDuration()));
						
						transferLeg.setTimeWindowStart(toLocalDateTime(leg.startTime).atZone(zone));
						transferLeg.setTimeWindowEnd(toLocalDateTime(leg.endTime).atZone(zone));
						
            legStructure.setTransferLeg(transferLeg);
					}else {
						TimedLegStructure timedLeg = new TimedLegStructure();
						LegBoardStructure board = new LegBoardStructure();
						LegAlightStructure alight = new LegAlightStructure();

						ApiPlace from = leg.from;
						ApiPlace to = leg.to;

						int sequence = 1;

						board.setStopPointRef(new StopPointRefStructure().withValue(from.stopId));
                		
						board.setStopPointName(getInternationName(from.name,lang) );
						board.setOrder(BigInteger.valueOf(sequence));
						
						
						if(from.arrival != null) {
							board.setServiceArrival(
									new LegBoardStructure.ServiceArrival()
										.withTimetabledTime(toLocalDateTime(from.arrival).atZone(zone))
										.withEstimatedTime(toLocalDateTime(from.arrival).plusSeconds(leg.arrivalDelay).atZone(zone))
									);
						}
						
						board.setServiceDeparture(
								new LegBoardStructure.ServiceDeparture()
									.withTimetabledTime(toLocalDateTime(from.departure).atZone(zone))
									.withEstimatedTime(toLocalDateTime(from.departure).plusSeconds(leg.departureDelay).atZone(zone))
								);
						
						allMyPlaces.add(
							new PlaceStructure()
							.withLocationName(getInternationName(from.name,lang))
							.withStopPoint(new StopPointStructure()
									.withStopPointRef(
											new StopPointRefStructure().withValue(from.stopId)
											)
									.withStopPointName(getInternationName(from.name,lang))
									)
							.withGeoPosition(
									new LocationStructure()
									.withLatitude(BigDecimal.valueOf(from.lat))
									.withLongitude(BigDecimal.valueOf(from.lon))));
						
												
						for(ApiPlace stop: leg.intermediateStops) {
							sequence += 1;
							if(includeIntermediateStops) {
								LegIntermediateStructure intermediateStop = new LegIntermediateStructure();
								intermediateStop.setOrder(BigInteger.valueOf(sequence));
								intermediateStop.setStopPointRef(new StopPointRefStructure().withValue(stop.stopId));
								
								
								intermediateStop.setStopPointName(getInternationName(stop.name,lang));

								intermediateStop.setServiceArrival(
									new LegIntermediateStructure.ServiceArrival()
									.withTimetabledTime(toLocalDateTime(stop.arrival).atZone(zone))
									.withEstimatedTime(toLocalDateTime(stop.arrival).plusSeconds(leg.arrivalDelay).atZone(zone))
									);
								intermediateStop.setServiceDeparture(
									new LegIntermediateStructure.ServiceDeparture()
									.withTimetabledTime(toLocalDateTime(stop.departure).atZone(zone))
									.withEstimatedTime(toLocalDateTime(stop.departure).plusSeconds(leg.departureDelay).atZone(zone))
									);
								
								timedLeg.getLegIntermediates().add(intermediateStop );
								allMyPlaces.add(
									new PlaceStructure()
									.withLocationName(getInternationName(stop.name,lang))
									.withStopPoint(new StopPointStructure()
											.withStopPointRef(
													new StopPointRefStructure().withValue(stop.stopId)
													)
											.withStopPointName(getInternationName(stop.name,lang))
											)
									.withGeoPosition(
											new LocationStructure()
											.withLatitude(BigDecimal.valueOf(stop.lat))
											.withLongitude(BigDecimal.valueOf(stop.lon))));
							}
						}
                		
						alight.setStopPointRef(new StopPointRefStructure().withValue(to.stopId));
						
                		alight.setStopPointName(getInternationName(to.name,lang) );
                		alight.setOrder(BigInteger.valueOf(sequence+1));
                		
                		alight.setServiceArrival(new LegAlightStructure.ServiceArrival()
                				.withTimetabledTime(toLocalDateTime(to.arrival).atZone(zone))
                				.withEstimatedTime(toLocalDateTime(to.arrival).plusSeconds(leg.arrivalDelay).atZone(zone))
                				);
                		
                		
                		if(to.departure != null) {
                			alight.setServiceDeparture(new LegAlightStructure.ServiceDeparture()
                    				.withTimetabledTime(toLocalDateTime(to.departure).atZone(zone))
                    				.withEstimatedTime(toLocalDateTime(to.departure).plusSeconds(leg.departureDelay).atZone(zone))
                    				);
                		}
                		
                		
                		allMyPlaces.add(
            					new PlaceStructure()
            					.withLocationName(getInternationName(to.name,lang))
            					.withStopPoint(new StopPointStructure()
            							.withStopPointRef(
            									new StopPointRefStructure().withValue(to.stopId)
            									)
            							.withStopPointName(getInternationName(to.name,lang))
            							)
            					
            					.withGeoPosition(
            							new LocationStructure()
            							.withLatitude(BigDecimal.valueOf(to.lat))
            							.withLongitude(BigDecimal.valueOf(to.lon))));
                		
						timedLeg.setLegBoard(board);
						timedLeg.setLegAlight(alight );
                		
						DatedJourneyStructure dj = factory.createDatedJourneyStructure();
																	
						dj.getContent().add(factory.createOperatorRef(new OperatorRefStructure().withValue(leg.agencyId)));
						dj.getContent().add(factory.createDatedJourneyStructureLineRef(new LineRefStructure().withValue(leg.routeId)));
						
						List<StopLocation> allStops = transitService().getPatternForTrip(trip(leg.tripId)).getStops();

						StopLocation originTripStop = allStops.get(0);
						StopLocation destinationTripStop = allStops.get(allStops.size()-1);



						
						dj.getContent().add(factory.createDatedJourneyStructureOriginStopPointRef(
								new StopPointRefStructure()
									.withValue(
											originTripStop.getId().toString()
											)
									)
								);
						
						allMyPlaces.add(
            					new PlaceStructure()
            					.withLocationName(getInternationName(originTripStop.getName().toString(),lang))
            					.withStopPoint(new StopPointStructure()
            							.withStopPointRef(
            									new StopPointRefStructure().withValue(originTripStop.getId().toString())
            									)
            							.withStopPointName(getInternationName(originTripStop.getName().toString(),lang))
            							)
            					.withGeoPosition(
            							new LocationStructure()
            							.withLatitude(BigDecimal.valueOf(originTripStop.getLat()))
            							.withLongitude(BigDecimal.valueOf(originTripStop.getLon()))));
						
						dj.getContent().add(factory.createDatedJourneyStructureDestinationStopPointRef(
								new StopPointRefStructure()
									.withValue(
											destinationTripStop.getId().toString()
											)
									)
								);
						
						allMyPlaces.add(
            					new PlaceStructure()
            					.withLocationName(getInternationName(destinationTripStop.getName().toString(),lang))
            					.withStopPoint(new StopPointStructure()
            							.withStopPointRef(
            									new StopPointRefStructure().withValue(destinationTripStop.getId().toString())
            									)
            							.withStopPointName(getInternationName(destinationTripStop.getName().toString(),lang))
            							)
            					.withGeoPosition(
            							new LocationStructure()
            							.withLatitude(BigDecimal.valueOf(destinationTripStop.getLat()))
            							.withLongitude(BigDecimal.valueOf(destinationTripStop.getLon()))));
						
						dj.getContent().add(factory.createDatedJourneyStructureOriginText(getInternationName(allStops.get(0).getName().toString(),lang)));
						dj.getContent().add(factory.createDatedJourneyStructureDestinationText(getInternationName(allStops.get(allStops.size()-1).getName().toString(),lang)));
						
						dj.getContent().add(factory.createJourneyRef(new JourneyRefStructure().withValue(leg.tripId.toString())));
						dj.getContent().add(factory.createDatedJourneyStructurePublishedLineName(getInternationName(leg.route,lang)));
						dj.getContent().add(factory.createOperatingDayRef(new OperatingDayRefStructure().withValue(leg.serviceDate)));
						
						dj.getContent().add(factory.createDatedJourneyStructureMode(new ModeStructure().withPtMode(OJPCommon.convertOJPModes(leg.routeType))));
						
						
						timedLeg.setService(dj );
						
						if(includeTrack) {
							LegTrackStructure track = new LegTrackStructure();
							
							TrackSectionStructure tss = new TrackSectionStructure();
							LinkProjection projection = new LinkProjection();
							
							EncodedPolyline polyline = leg.legGeometry;
							List<Coordinate> coordinates = OJPCommon.decodePolyline(polyline);
									
							for(Coordinate coor : coordinates) {
								LocationStructure point = new LocationStructure();
								point.setLatitude(BigDecimal.valueOf(coor.y));
								point.setLongitude(BigDecimal.valueOf(coor.x));
								projection.getPosition().add(point);
							}				
						
							PlaceRefStructure pointStart = new PlaceRefStructure();
							PlaceRefStructure pointEnd = new PlaceRefStructure();
							
							pointStart.setLocationName(getInternationName(leg.from.name,lang));
							pointEnd.setLocationName(getInternationName(leg.to.name,lang));
							
							if(leg.from.stopId != null) {
								pointStart.setStopPointRef(new StopPointRefStructure().withValue(leg.from.stopId.toString()));
							}else {
								pointStart.setGeoPosition(new LocationStructure().withLatitude(BigDecimal.valueOf(leg.from.lat)).withLongitude(BigDecimal.valueOf(leg.from.lon)));
	                		}
							
							if(leg.to.stopId != null) {
								pointEnd.setStopPointRef(new StopPointRefStructure().withValue(leg.to.stopId.toString()));
							}else {
								pointEnd.setGeoPosition(new LocationStructure().withLatitude(BigDecimal.valueOf(leg.to.lat)).withLongitude(BigDecimal.valueOf(leg.to.lon)));
	                		}
							
							tss.setTrackStart(pointStart);
							tss.setTrackEnd(pointEnd);
							
							
							tss.setLinkProjection(projection );
							track.getTrackSection().add(tss );
							timedLeg.setLegTrack(track );
							
						}
                		
                		legStructure.setTimedLeg(timedLeg);
                	}
                	legStructure.setLegId(randomUUID());
                	
    				tripStructure.getTripLeg().add(legStructure);
            	}
            	
            	tripStructure.setDistance(BigInteger.valueOf(tripDistance));
				tripResult.setTrip(tripStructure);
				trip.getTripResult().add(tripResult);
				
			}
            
			allPlaces.getLocation().addAll(new ArrayList<PlaceStructure>(allMyPlaces));
			TripResponseContextStructure context = new TripResponseContextStructure();
			context.setPlaces(allPlaces );
			trip.setTripResponseContext(context );  
           
		} catch (Exception e) {
			ServiceDeliveryErrorConditionStructure errorS = new ServiceDeliveryErrorConditionStructure();
			ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
			descr.setValue("Error"); //TODO: fix with a the real error, or codes (!?)
			trip.setStatus(false);
			trip.setErrorCondition(errorS.withDescription(descr ));
		} catch (Throwable t) {
				System.out.printf("Unchecked error while planning path: ", t);
				trip.setStatus(false);
				ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
				ErrorDescriptionStructure descr = new ErrorDescriptionStructure();
				descr.setValue("Unchecked error while planning path.");
				trip.setErrorCondition(error.withDescription(descr ));
		}
		
		long timeEnd = System.currentTimeMillis();
		trip.setCalcTime(BigInteger.valueOf(timeEnd - timeStart));
		return trip;
	}



	private InternationalTextStructure getInternationName(String text, String lang) {
		InternationalTextStructure name = new InternationalTextStructure();
		NaturalLanguageStringStructure tmpName = new NaturalLanguageStringStructure();
		tmpName.setValue(text);
		tmpName.setLang(lang);
		name.setText(tmpName );		
		return name;
	}
	
	private String randomUUID() {
		UUID uuid = UUID.randomUUID();
        String uuidAsString = uuid.toString();
        return uuidAsString;
	}
	
	private static LocalDateTime toLocalDateTime(Calendar calendar) {
        return LocalDateTime.ofInstant(calendar.toInstant(), ZoneId.systemDefault());
    }
	
	private GenericLocation toGenericLocation(double lng, double lat) {
        return new GenericLocation(lat, lng);
    }

	private RouteRequest createRequest(Map<String, Object> requestMap) {
		TransmodelRequestContext context = new TransmodelRequestContext(
			serverRequestContext,
			serverRequestContext.routingService(),
			serverRequestContext.transitService()
		);

		ExecutionInput executionInput = ExecutionInput
			.newExecutionInput()
			.query("")
			.operationName("trip")
			.context(context)
			.locale(Locale.ENGLISH)
			.build();

		ExecutionContext executionContext = newExecutionContextBuilder()
			.executionInput(executionInput)
			.executionId(ExecutionId.from(this.getClass().getName()))
			.build();

		DataFetchingEnvironment environment = DataFetchingEnvironmentImpl
			.newDataFetchingEnvironment(executionContext)
			.context(context)
			.arguments(requestMap)
			.build();


		return TripRequestMapper.createRequest(environment);
	}

	private TransitService transitService() {
		return this.serverRequestContext.transitService();
	}

	private Trip trip(String tripId) {
		var trip = transitService().getTripForId(OJPCommon.createId("tripId", tripId));
		return OJPCommon.validateExist("Trip", trip, "tripId", tripId);
	}

}
